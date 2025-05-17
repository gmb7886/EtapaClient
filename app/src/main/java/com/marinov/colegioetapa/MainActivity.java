package com.marinov.colegioetapa;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.color.MaterialColors;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_NOTIFICATION_PERMISSION = 100;
    private Fragment currentFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Verifica se a versão do Android é inferior a 6.0 (API 23)

        MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorPrimaryContainer,
                Color.BLACK);

        setContentView(R.layout.activity_main);

        // Ajuste de status/navigation bars
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);

            WindowInsetsControllerCompat insetsController =
                    ViewCompat.getWindowInsetsController(getWindow().getDecorView());
            if (insetsController != null) {
                insetsController.setAppearanceLightStatusBars(!isDarkMode());
            }
        } else {
            getWindow().setStatusBarColor(Color.BLACK);
            getWindow().setNavigationBarColor(Color.BLACK);
        }

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);

        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(v.getPaddingLeft(), statusBarHeight, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        View rootView = findViewById(R.id.main);

        // Oculta bottom nav quando teclado aparece
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            rootView.getWindowVisibleDisplayFrame(r);
            int screenHeight = rootView.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;
            bottomNav.setVisibility(keypadHeight > screenHeight * 0.15 ? View.GONE : View.VISIBLE);
        });

        bottomNav.setOnItemSelectedListener(this::handleNavigation);

        // Se veio extra para abrir Notas diretamente
        int targetItem = getIntent().getIntExtra("EXTRA_NAV_ITEM_ID", -1);
        if (targetItem == R.id.navigation_notas) {
            bottomNav.setSelectedItemId(targetItem);
        }

        if (savedInstanceState == null) {
            currentFragment = new HomeFragment();
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.nav_host_fragment, currentFragment)
                    .commit();
        }

        solicitarPermissaoNotificacao();
        iniciarNotasWorker();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        int target = intent.getIntExtra("EXTRA_NAV_ITEM_ID", -1);
        if (target == R.id.navigation_notas) {
            bottomNav.setSelectedItemId(target);
        }
    }

    private void solicitarPermissaoNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATION_PERMISSION
                );
            }
        }
    }

    private void iniciarNotasWorker() {
        PeriodicWorkRequest notasWork = new PeriodicWorkRequest.Builder(NotasWorker.class, 30, TimeUnit.MINUTES)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "NotasWorkerTask",
                ExistingPeriodicWorkPolicy.KEEP,
                notasWork
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private boolean isDarkMode() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private boolean handleNavigation(MenuItem item) {
        int id = item.getItemId();
        Fragment newFragment = null;

        if (id == R.id.navigation_home) {
            newFragment = new HomeFragment();
        } else if (id == R.id.option_calendario_provas) {
            newFragment = new CalendarioProvas();
        } else if (id == R.id.navigation_notas) {
            newFragment = new NotasFragment();
        } else if (id == R.id.option_horarios_aula) {
            newFragment = new HorariosAula();
        } else if (id == R.id.navigation_more) {
            showMoreOptions();
            return false;
        }

        if (newFragment != null && newFragment != currentFragment) {
            currentFragment = newFragment;
            getSupportFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .replace(R.id.nav_host_fragment, currentFragment)
                    .commit();
            return true;
        }
        return false;
    }

    @SuppressLint("InflateParams")
    private void showMoreOptions() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_more_options, null);

        view.findViewById(R.id.navigation_provas).setOnClickListener(v -> {
            currentFragment = new ProvasFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_host_fragment, currentFragment)
                    .commit();
            dialog.dismiss();
        });
        view.findViewById(R.id.option_digital).setOnClickListener(v -> {
            currentFragment = new DigitalFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_host_fragment, currentFragment)
                    .commit();
            dialog.dismiss();
        });

        view.findViewById(R.id.option_link).setOnClickListener(v -> {
            currentFragment = new LinkFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_host_fragment, currentFragment)
                    .commit();
            dialog.dismiss();
        });

        view.findViewById(R.id.option_acc_detalhes).setOnClickListener(v -> {
            currentFragment = new AccFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_host_fragment, currentFragment)
                    .commit();
            dialog.dismiss();
        });

        view.findViewById(R.id.option_acc_inscricao).setOnClickListener(v -> {
            currentFragment = new InscricaoAccFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_host_fragment, currentFragment)
                    .commit();
            dialog.dismiss();
        });

        view.findViewById(R.id.option_escreve_enviar).setOnClickListener(v -> {
            currentFragment = new EscreveEnviarFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_host_fragment, currentFragment)
                    .commit();
            dialog.dismiss();
        });

        view.findViewById(R.id.option_escreve_ver).setOnClickListener(v -> {
            currentFragment = new EscreveVerFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_host_fragment, currentFragment)
                    .commit();
            dialog.dismiss();
        });

        view.findViewById(R.id.option_redacao_semanal).setOnClickListener(v -> {
            currentFragment = new RedacaoSemanalFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_host_fragment, currentFragment)
                    .commit();
            dialog.dismiss();
        });

        view.findViewById(R.id.option_boletim_simulados).setOnClickListener(v -> {
            currentFragment = new BoletimSimuladosFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_host_fragment, currentFragment)
                    .commit();
            dialog.dismiss();
        });

        view.findViewById(R.id.option_graficos).setOnClickListener(v -> {
            currentFragment = new GraficosFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_host_fragment, currentFragment)
                    .commit();
            dialog.dismiss();
        });

        view.findViewById(R.id.option_provas_gabaritos).setOnClickListener(v -> {
            currentFragment = new ProvasGabaritos();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_host_fragment, currentFragment)
                    .commit();
            dialog.dismiss();
        });

        view.findViewById(R.id.option_detalhes_provas).setOnClickListener(v -> {
            currentFragment = new DetalhesProvas();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_host_fragment, currentFragment)
                    .commit();
            dialog.dismiss();
        });
        view.findViewById(R.id.navigation_material).setOnClickListener(v -> {
            currentFragment = new MaterialFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_host_fragment, currentFragment)
                    .commit();
            dialog.dismiss();
        });

        view.findViewById(R.id.option_plantao_duvidas).setOnClickListener(v -> {
            currentFragment = new PlantaoDuvidas();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_host_fragment, currentFragment)
                    .commit();
            dialog.dismiss();
        });

        view.findViewById(R.id.option_food).setOnClickListener(v -> {
            currentFragment = new CardapioFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_host_fragment, currentFragment)
                    .commit();
            dialog.dismiss();
        });

        view.findViewById(R.id.option_plantao_duvidas_online).setOnClickListener(v -> {
            currentFragment = new PlantaoDuvidasOnline();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_host_fragment, currentFragment)
                    .commit();
            dialog.dismiss();
        });
        view.findViewById(R.id.option_ead).setOnClickListener(v -> {
            currentFragment = new EADFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_host_fragment, currentFragment)
                    .commit();
            dialog.dismiss();
        });

        dialog.setContentView(view);
        dialog.show();
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.top_app_bar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        else if (id == R.id.action_profile) {
            currentFragment = new ProfileFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_host_fragment, currentFragment)
                    .commit();
        }

        return super.onOptionsItemSelected(item);
    }
}
