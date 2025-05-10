package com.marinov.colegioetapa;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ProvasFragment extends Fragment {
    private static final String TAG = "ProvasFragment";
    private SearchView searchView;
    private ListView listView;
    private ProgressBar progressBar;
    private RepoAdapter adapter;
    private List<RepoItem> repoItems = new ArrayList<>();
    private String currentPath = "";
    private FetchFilesTask fetchTask;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_provas, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        searchView = view.findViewById(R.id.search_view);
        listView = view.findViewById(R.id.listView);
        progressBar = view.findViewById(R.id.progress_circular);

        adapter = new RepoAdapter(requireContext(), repoItems);
        listView.setAdapter(adapter);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) {
                adapter.getFilter().filter(query);
                return true;
            }
            @Override public boolean onQueryTextChange(String newText) {
                adapter.getFilter().filter(newText);
                return true;
            }
        });

        listView.setOnItemClickListener((parent, v, pos, id) -> {
            RepoItem item = adapter.getItem(pos);
            if (item == null) return;
            if ("dir".equals(item.type)) {
                currentPath = item.path;
                startFetch();
            } else {
                startDownload(item);
            }
        });

        startFetch();
    }

    private void startDownload(RepoItem item) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(item.downloadUrl));
        request.setMimeType("*/*");
        request.addRequestHeader("User-Agent", "EtapaApp");
        request.setTitle(item.name);
        request.setDescription("Baixando arquivo...");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, item.name);
        DownloadManager dm = (DownloadManager) requireContext().getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm != null) {
            dm.enqueue(request);
            Toast.makeText(requireContext(), "Download iniciado: " + item.name, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (fetchTask != null) fetchTask.cancel(true);
    }

    private void startFetch() {
        if (fetchTask != null) fetchTask.cancel(true);
        fetchTask = new FetchFilesTask(this);
        fetchTask.execute(currentPath);
    }

    private static class RepoItem {
        String name, type, path, downloadUrl;
        RepoItem(String name, String type, String path, String downloadUrl) {
            this.name = name; this.type = type; this.path = path; this.downloadUrl = downloadUrl;
        }
        @NonNull
        @Override
        public String toString() { return name; }
    }

    private static class RepoAdapter extends ArrayAdapter<RepoItem> {
        private final List<RepoItem> original;
        RepoAdapter(Context ctx, List<RepoItem> items) {
            super(ctx, R.layout.item_repo, new ArrayList<>());
            this.original = new ArrayList<>(items);
        }

        @NonNull
        @Override
        public View getView(int pos, View convertView, @NonNull ViewGroup parent) {
            View row = convertView != null ? convertView :
                    LayoutInflater.from(getContext()).inflate(R.layout.item_repo, parent, false);
            ImageView icon = row.findViewById(R.id.item_icon);
            TextView text = row.findViewById(R.id.item_text);
            RepoItem item = getItem(pos);
            if (item != null) {
                text.setText(item.name);
                icon.setImageResource(item.type.equals("dir") ? R.drawable.ic_folder : R.drawable.ic_file);
            }
            return row;
        }

        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    List<RepoItem> filtered = new ArrayList<>();
                    if (constraint == null || constraint.length() == 0) {
                        filtered.addAll(original);
                    } else {
                        String q = constraint.toString().toLowerCase();
                        for (RepoItem ri : original) {
                            if (ri.name.toLowerCase().contains(q)) filtered.add(ri);
                        }
                    }
                    FilterResults results = new FilterResults();
                    results.values = filtered;
                    results.count = filtered.size();
                    return results;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    clear();
                    //noinspection unchecked
                    addAll((List<RepoItem>) results.values);
                    notifyDataSetChanged();
                }
            };
        }

        public void setOriginal(List<RepoItem> items) {
            original.clear();
            original.addAll(items);
        }
    }

    private static class FetchFilesTask extends AsyncTask<String, Void, List<RepoItem>> {
        private final WeakReference<ProvasFragment> fragRef;
        FetchFilesTask(ProvasFragment frag) { fragRef = new WeakReference<>(frag); }

        @Override
        protected void onPreExecute() {
            ProvasFragment f = fragRef.get(); if (f == null || !f.isAdded()) return;
            f.progressBar.setVisibility(View.VISIBLE);
            f.repoItems.clear();
            f.adapter.clear();
            f.adapter.setOriginal(new ArrayList<>());
        }

        @Override
        protected List<RepoItem> doInBackground(String... p) {
            String path = p.length > 0 ? p[0] : "";
            List<RepoItem> list = new ArrayList<>();
            HttpURLConnection c = null;

            try {
                String api = "https://api.github.com/repos/gmb7886/schooltests/contents" +
                        (path.isEmpty() ? "" : "/" + path);

                Log.d(TAG, "GitHub API URL: " + api); // <-- 🔍 LOG ADICIONADO AQUI

                URL u = new URL(api);
                c = (HttpURLConnection) u.openConnection();
                c.setRequestMethod("GET");
                c.setRequestProperty("Accept", "application/vnd.github.v3+json");
                c.setRequestProperty("User-Agent", "EtapaApp");
                c.setRequestProperty("Authorization", "Bearer TOKEN SACANA");

                int responseCode = c.getResponseCode();
                Log.d(TAG, "HTTP response code: " + responseCode);

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    InputStream errorStream = c.getErrorStream();
                    if (errorStream != null) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream));
                        StringBuilder error = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) error.append(line);
                        reader.close();
                        Log.e(TAG, "GitHub API error: " + error.toString());
                    }
                    return list;
                }

                if (isCancelled()) return list;

                InputStream is = c.getInputStream();
                BufferedReader r = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String l;
                while ((l = r.readLine()) != null) sb.append(l);
                r.close();

                JSONArray arr = new JSONArray(sb.toString());
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    list.add(new RepoItem(
                            o.getString("name"),
                            o.getString("type"),
                            o.getString("path"),
                            o.optString("download_url", "")
                    ));
                }

            } catch (Exception e) {
                Log.e(TAG, "Erro fetch", e);
            } finally {
                if (c != null) c.disconnect();
            }

            return list;
        }

        @Override
        protected void onPostExecute(List<RepoItem> res) {
            ProvasFragment f = fragRef.get(); if (f == null || !f.isAdded()) return;
            f.progressBar.setVisibility(View.GONE);
            if (res != null && !res.isEmpty()) {
                f.repoItems.addAll(res);
                f.adapter.setOriginal(res);
                f.adapter.clear();
                f.adapter.addAll(res);
            } else {
                f.adapter.clear();
            }
            f.adapter.notifyDataSetChanged();
        }
    }
}
