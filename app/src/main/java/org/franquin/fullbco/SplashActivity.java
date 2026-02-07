package org.franquin.fullbco;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;


public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash); // Asegúrate de usar el nombre correcto de tu archivo de layout

        // Duración del splash screen (por ejemplo, 3000ms = 3 segundos)
        int SPLASH_TIME_OUT = 2000;
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                // Iniciar la actividad principal una vez finalizado el tiempo del splash screen
                Intent i = new Intent(SplashActivity.this, FullscreenActivity.class);
                startActivity(i);

                // Cerrar esta actividad
                finish();
            }
        }, SPLASH_TIME_OUT);
    }
}
