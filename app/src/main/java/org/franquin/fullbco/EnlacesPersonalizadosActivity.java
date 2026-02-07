package org.franquin.fullbco;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Map;


public class EnlacesPersonalizadosActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private GridLayout gridEnlaces;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enlaces_personalizados);

        prefs = getSharedPreferences("MisEnlaces", Context.MODE_PRIVATE);
        gridEnlaces = findViewById(R.id.gridEnlaces);
        Button btnAddEnlace = findViewById(R.id.btnAddEnlace);

        btnAddEnlace.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mostrarDialogoAgregarEditar(null);
            }
        });

        cargarEnlaces();
    }

    private void cargarEnlaces() {
        // Asegúrate de que esta línea se encuentra después de setContentView en onCreate
        gridEnlaces = findViewById(R.id.gridEnlaces);

        Map<String, ?> enlaces = prefs.getAll();

        int[] botonesIds = {R.id.boton_01, R.id.boton_02, R.id.boton_03,
                R.id.boton_04, R.id.boton_05, R.id.boton_06,
                R.id.boton_07, R.id.boton_08, R.id.boton_09};

        int i = 0;
        for (Map.Entry<String, ?> entry : enlaces.entrySet()) {
            if (i >= botonesIds.length) break;

            String titulo = entry.getKey();
            String url = entry.getValue().toString();

            Button btnEnlace = findViewById(botonesIds[i]);
            if (btnEnlace != null) { // Añade esta comprobación para evitar NullPointerException
                btnEnlace.setText(titulo);
                btnEnlace.setVisibility(View.VISIBLE);

                btnEnlace.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent data = new Intent();
                        data.putExtra("urlSeleccionado", url); // 'url' es el URL asociado con el botón
                        setResult(RESULT_OK, data);
                        finish(); // Cierra la actividad y devuelve el resultado a FullscreenActivity
                    }
                });

                btnEnlace.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        mostrarOpcionesEnlace(titulo);
                        return true;
                    }
                });
            } else {
                Log.e("cargarEnlaces", "Botón no encontrado para ID: " + botonesIds[i]);
            }
            i++;
        }

        // Ocultar botones restantes si hay menos de 9 enlaces
        for (; i < botonesIds.length; i++) {
            Button btnEnlace = findViewById(botonesIds[i]);
            if (btnEnlace != null) {
                btnEnlace.setVisibility(View.GONE);
            } else {
                Log.e("cargarEnlaces", "Botón no encontrado para ID: " + botonesIds[i]);
            }
        }
    }



    private void mostrarDialogoAgregarEditar(String titulo) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final View customLayout = getLayoutInflater().inflate(R.layout.dialog_enlace, null);
        builder.setView(customLayout);

        EditText etTitulo = customLayout.findViewById(R.id.etTitulo);
        EditText etUrl = customLayout.findViewById(R.id.etUrl);

        if (titulo != null) {
            etTitulo.setText(titulo);
            etUrl.setText(prefs.getString(titulo, "").replace("?mode=kiosk&hidetoolbar", ""));
        }

        builder.setPositiveButton(titulo == null ? "Agregar" : "Editar", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String nuevoTitulo = etTitulo.getText().toString();
                String nuevaUrl = etUrl.getText().toString();

                if (!nuevaUrl.startsWith("https://eworkerbrrc.endesa.es/PIVision/") &&
                        !nuevaUrl.startsWith("https://emsipw305/PIVision/")) {
                    Toast.makeText(EnlacesPersonalizadosActivity.this, "La URL debe comenzar con 'https://eworkerbrrc.endesa.es/PIVision/' o 'https://emsipw305/PIVision/'", Toast.LENGTH_LONG).show();

                    mostrarDialogoAgregarEditar(titulo != null ? titulo : nuevoTitulo);
                    return;
                }


                if (nuevaUrl.startsWith("https://emsipw305/PIVision/")) {
                    nuevaUrl = nuevaUrl.replace("https://emsipw305/PIVision/", "https://eworkerbrrc.endesa.es/PIVision/");
                }

                nuevaUrl += "?mode=kiosk&hidetoolbar";

                SharedPreferences.Editor editor = prefs.edit();
                if (titulo != null && !titulo.equals(nuevoTitulo)) {
                    editor.remove(titulo); // Eliminar el enlace antiguo si el título cambió
                }
                editor.putString(nuevoTitulo, nuevaUrl);
                editor.apply();
                cargarEnlaces();
            }
        });

        builder.setNegativeButton("Cancelar", null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void mostrarOpcionesEnlace(String titulo) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Opciones para " + titulo);

        builder.setItems(new String[]{"Editar", "Eliminar"}, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) { // Editar
                    mostrarDialogoAgregarEditar(titulo);
                } else if (which == 1) { // Eliminar
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.remove(titulo);
                    editor.apply();
                    cargarEnlaces();
                }
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }
}
