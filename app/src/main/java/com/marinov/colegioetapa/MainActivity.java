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
        View rootView = findViewById(R.id.main);          // seu CoordinatorLayout ou root
        View navHost = findViewById(R.id.nav_host_fragment);

        // Ajusta padding inferior do navHost para dar espaço à BottomNavigationView
        bottomNav.post(() -> navHost.setPadding(
                navHost.getPaddingLeft(),
                navHost.getPaddingTop(),
                navHost.getPaddingRight(),
                bottomNav.getHeight()
        ));

        // Oculta/mostra BottomNavigationView quando o teclado aparece/desaparece
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            rootView.getWindowVisibleDisplayFrame(r);
            int screenHeight = rootView.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;
            bottomNav.setVisibility(
                    keypadHeight > screenHeight * 0.15 ? View.GONE : View.VISIBLE
            );
        });

        // Configura o listener dos itens
        bottomNav.setOnItemSelectedListener(this::onNavigationItemSelected);

        // Fragmento inicial (Home)
        if (savedInstanceState == null) {
            currentFragment = new HomeFragment();
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.nav_host_fragment, currentFragment)
                    .commit();
        }
    }

    private boolean onNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();
        Fragment newFragment = null;

        if (id == R.id.navigation_home) {
            newFragment = new HomeFragment();
        }
        else if (id == R.id.navigation_provas) {
            newFragment = new ProvasFragment();
        }

        if (newFragment != null && newFragment != currentFragment) {
            currentFragment = newFragment;
            getSupportFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(
                            android.R.anim.fade_in,
                            android.R.anim.fade_out
                    )
                    .replace(R.id.nav_host_fragment, currentFragment)
                    .commit();
        }
        return true;
    }
    }
