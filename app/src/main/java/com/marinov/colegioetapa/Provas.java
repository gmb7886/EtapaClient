package com.marinov.colegioetapa;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class Provas extends Fragment {

    public Provas() {
        // construtor vazio é recomendado
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // inflamos um layout simples para prototipar
        return inflater.inflate(R.layout.fragment_provas, container, false);
    }
}
