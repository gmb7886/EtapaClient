package com.marinov.colegioetapa;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private Fragment currentFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        View rootView = findViewById(R.id.main); // ID do CoordinatorLayout
        View navHost = findViewById(R.id.nav_host_fragment);

        // Ajusta o padding inferior do navHost de acordo com a altura da barra
        bottomNav.post(() -> navHost.setPadding(
                navHost.getPaddingLeft(),
                navHost.getPaddingTop(),
                navHost.getPaddingRight(),
                bottomNav.getHeight()
        ));

        // Oculta a BottomNavigationView quando o teclado estiver visível
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            rootView.getWindowVisibleDisplayFrame(r);
            int screenHeight = rootView.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;

            if (keypadHeight > screenHeight * 0.15) {
                bottomNav.setVisibility(View.GONE); // Teclado visível
            } else {
                bottomNav.setVisibility(View.VISIBLE); // Teclado oculto
            }
        });

        // Listener de seleção dos itens
        bottomNav.setOnItemSelectedListener(this::onNavigationItemSelected);

        // Carrega o fragmento inicial
        if (savedInstanceState == null) {
            currentFragment = new HomeFragment();
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.nav_host_fragment, currentFragment)
                    .commit();
        }
    }

    private boolean onNavigationItemSelected(MenuItem item) {
        Fragment newFragment;

        if (item.getItemId() == R.id.navigation_provas) {
            newFragment = new Provas();
        } else {
            newFragment = new HomeFragment();
        }

        if (newFragment != currentFragment) {
            currentFragment = newFragment;
            getSupportFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .replace(R.id.nav_host_fragment, currentFragment)
                    .commit();
        }
        return true;
    }
}
