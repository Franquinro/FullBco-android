package org.franquin.fullbco;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.Log;
import android.util.TypedValue;
import android.text.style.BulletSpan;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.HttpAuthHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Objects;

public class FullscreenActivity extends FragmentActivity {

    // -------------------------------------------------------------------------------------------
    // Ajustar a tu propia URL de inicio y JSON de versión
    // -------------------------------------------------------------------------------------------
    public static final String URL_INICIO = "https://eworkerbrrc.endesa.es/PIVision/#/Displays/54936/MENU?mode=kiosk&hidetoolbar&redirect=false";
    public static final String APP_VERSION_JSON = "https://fqrdev.netlify.app/fullbco/version.json";
    private static final String TAG = "FullBco";
    private static final int REQUEST_CODE_ENLACES_PERSONALIZADOS = 1;

    // -------------------------------------------------------------------------------------------
    // WebView y referencias a UI
    // -------------------------------------------------------------------------------------------
    private static WebView vistaweb;
    private SharedPreferences pref;
    private SharedPreferences.Editor editor;

    private SeekBar barraTop, barraBottom;
    private Button botonHide;
    private View ajusteView;
    private boolean zoom;
    private ImageView botonZoom, botonMenu;

    /**
     * Controla si ya hemos intentado usar las credenciales guardadas en esta sesión
     * para evitar bucles en caso de que estén caducadas.
     */
    private boolean triedSavedCredentials = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pref = PreferenceManager.getDefaultSharedPreferences(this);
        editor = pref.edit();

        setContentView(R.layout.activity_fullscreen);
        checkForUpdates();  // si tienes lógica de actualizaciones

        // ----------------------------------
        // Inicialización de vistas
        // ----------------------------------
        zoom = true;
        botonZoom = findViewById(R.id.boton_zoom);
        ajusteView = findViewById(R.id.ajuste_view);
        botonHide = findViewById(R.id.botonHide);
        barraTop = findViewById(R.id.seekBarTop);
        barraBottom = findViewById(R.id.seekBarBottom);
        botonMenu = findViewById(R.id.boton_menu);

        // Leer valores de márgenes guardados
        barraTop.setProgress(Integer.parseInt(Objects.requireNonNull(pref.getString("top_margen", "0"))));
        barraBottom.setProgress(Integer.parseInt(Objects.requireNonNull(pref.getString("bot_margen", "0"))));
        ajusteView.setVisibility(View.GONE);

        registerForContextMenu(botonMenu);
        botonMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openContextMenu(botonMenu);
            }
        });

        vistaweb = findViewById(R.id.vistaweb);
        ajustaView();  // Ajustar márgenes iniciales
        vistaweb.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return true;
            }
        });

        // Configuración básica del WebView
        String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.150 Safari/537.36";
        vistaweb.getSettings().setJavaScriptEnabled(true);
        vistaweb.getSettings().setUserAgentString(ua);
        vistaweb.getSettings().setDomStorageEnabled(true);
        vistaweb.getSettings().setUseWideViewPort(true);
        WebView.setWebContentsDebuggingEnabled(true);

        // ----------------------------------
        // Establecer WebViewClient
        // ----------------------------------
        vistaweb.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Ejemplo de reemplazo de URL
                String url = request.getUrl().toString();
                if (url.contains("emsipw305")) {
                    String newUrl = url.replace("http://emsipw305", "https://eworkerbrrc.endesa.es")
                            .replace("?mode=kiosk&hidetoolbar", "")
                            .replace("&redirect=false", "")
                            + "?mode=kiosk&hidetoolbar&redirect=false";
                    view.loadUrl(newUrl);
                    return true;
                }
                return false; // no sobreescribimos otras URLs
            }

            @Override
            public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {
                String usuario = pref.getString("usuario", "");
                String pass = pref.getString("pass", "");
                boolean savedCredentials = pref.getBoolean("save_credentials", false);

                // 1) Si todavía NO hemos intentado usar las credenciales guardadas:
                if (!triedSavedCredentials && savedCredentials && isNotEmpty(usuario) && isNotEmpty(pass)) {
                    triedSavedCredentials = true;  // marcamos que ya intentamos
                    // Intentamos con credenciales guardadas
                    handler.proceed("enelint\\" + usuario, pass);

                } else {
                    // 2) Si ya las intentamos una vez (y fallaron) o no están guardadas,
                    //    pedimos al usuario credenciales nuevas.
                    pedirCredenciales(null);
                    // NOTA: No llamamos a handler.cancel() porque a veces
                    //       el servidor seguirá pidiendo credenciales.
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                // Resetear historial si estamos en la URL de inicio
                if (url != null && url.equalsIgnoreCase(URL_INICIO)) {
                    vistaweb.clearHistory();
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);

                // Posible detección de error de autenticación
                String errorDescription = error.getDescription().toString().toLowerCase();
                if (errorDescription.contains("auth")
                        || errorDescription.contains("unauthorized")
                        || errorDescription.contains("forbidden")
                        || errorDescription.contains("too_many_retries")) {

                    // 1) Borramos las credenciales guardadas para evitar que
                    //    se reintenten en bucle.
                    editor.remove("usuario");
                    editor.remove("pass");
                    editor.remove("save_credentials");
                    editor.apply();

                    // 2) Volvemos a pedir nuevas credenciales al usuario
                    pedirCredenciales("Error de autenticación. Por favor, revisa tu usuario y contraseña.");
                }
            }
        });

        // Carga de URL inicial
        vistaweb.loadUrl(URL_INICIO);
        setDesktopMode(vistaweb, true);

        // Listeners de SeekBar para ajustar márgenes
        barraTop.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                ajustaViewTop(i);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        barraBottom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                ajustaViewBot(i);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        // Botón para ocultar la vista de ajustes
        botonHide.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                ajusteView.setVisibility(View.GONE);
            }
        });

        // Botón de zoom para alternar entre ajustado y pantalla completa
        botonZoom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (zoom) {
                    zoom = false;
                    botonZoom.setImageDrawable(getDrawable(R.drawable.ic_action_zoom_out));
                    FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
                    layoutParams.setMargins(0, 0, 0, 0);
                    vistaweb.setLayoutParams(layoutParams);
                } else {
                    botonZoom.setImageDrawable(getDrawable(R.drawable.ic_action_zoom));
                    zoom = true;
                    ajustaView();
                }
            }
        });
    }

    // ----------------------------------------------------------------------------
    // Pedir credenciales al usuario
    //
    // Si el parámetro 'mensaje' no es null, se mostrará en el diálogo para
    // avisar del motivo (por ejemplo, "Contraseña caducada").
    // ----------------------------------------------------------------------------
    private void pedirCredenciales(String mensaje) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Autenticación Requerida");

        if (mensaje != null && !mensaje.trim().isEmpty()) {
            builder.setMessage(mensaje);
        }

        View viewInflated = LayoutInflater.from(this)
                .inflate(R.layout.dialog_login, (ViewGroup) findViewById(android.R.id.content), false);

        final EditText inputUsuario = viewInflated.findViewById(R.id.input_usuario);
        final EditText inputPass = viewInflated.findViewById(R.id.input_pass);
        final CheckBox checkboxSave = viewInflated.findViewById(R.id.checkbox_save);

        // Mostrar el último usuario guardado (si existe)
        String savedUser = pref.getString("usuario", "");
        if (!savedUser.isEmpty()) {
            inputUsuario.setText(savedUser);
        }

        builder.setView(viewInflated);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                String usuario = inputUsuario.getText().toString();
                String pass = inputPass.getText().toString();

                boolean saveCredentials = checkboxSave.isChecked();
                editor.putBoolean("save_credentials", saveCredentials);

                if (saveCredentials) {
                    editor.putString("usuario", usuario);
                    editor.putString("pass", pass);
                } else {
                    editor.remove("usuario");
                    editor.remove("pass");
                }
                editor.apply();

                // Importante: cuando el usuario introduzca credenciales nuevas,
                // reseteamos triedSavedCredentials para darles un chance en
                // la próxima llamada onReceivedHttpAuthRequest.
                triedSavedCredentials = false;

                // Recargamos la página para que se dispare de nuevo la auth request
                vistaweb.reload();
            }
        });

        builder.setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
                // Se puede llamar a handler.cancel() si tuvieras el handler,
                // pero aquí no lo guardamos. Simplemente el servidor quedará sin credenciales.
            }
        });

        builder.show();
    }

    // ----------------------------------------------------------------------------
    // Manejo del botón Atrás
    // ----------------------------------------------------------------------------
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (vistaweb.canGoBack()) {
            vistaweb.goBack();
        } else {
            String currentUrl = vistaweb.getUrl();
            if (currentUrl != null && !currentUrl.equalsIgnoreCase(URL_INICIO)) {
                // Si la URL actual no es la de inicio, volvemos a inicio
                vistaweb.loadUrl(URL_INICIO);
            } else {
                // Si estamos en inicio, preguntamos al usuario
                new AlertDialog.Builder(this)
                        .setTitle("Salir de la aplicación")
                        .setMessage("No hay más páginas a las que retroceder. ¿Qué desea hacer?")
                        .setPositiveButton("Cerrar", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        })
                        .setNeutralButton("Inicio", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                vistaweb.loadUrl(URL_INICIO);
                            }
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
            }
        }
    }

    // ----------------------------------------------------------------------------
    // onActivityResult para otras pantallas (Ajustes, Enlaces, etc.)
    // ----------------------------------------------------------------------------
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 33) {
            ajustaView();
            vistaweb.clearCache(true);
            vistaweb.loadUrl(URL_INICIO);
            vistaweb.reload();
        }
        if (requestCode == REQUEST_CODE_ENLACES_PERSONALIZADOS && resultCode == RESULT_OK && data != null) {
            String url = data.getStringExtra("urlSeleccionado");
            if (url != null) {
                vistaweb.loadUrl(url);
            }
        }
    }

    // ----------------------------------------------------------------------------
    // Menú contextual
    // ----------------------------------------------------------------------------
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        getMenuInflater().inflate(R.menu.menu_contextual, menu);
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.inicio:
                vistaweb.loadUrl("about:blank");
                vistaweb.clearCache(true);
                vistaweb.loadUrl(URL_INICIO);
                return true;
            case R.id.personales:
                Intent intent = new Intent(this, EnlacesPersonalizadosActivity.class);
                startActivityForResult(intent, REQUEST_CODE_ENLACES_PERSONALIZADOS);
                return true;
            case R.id.salir:
                finish();
                return true;
            case R.id.configuracion:
                startActivityForResult(new Intent(this, SettingsActivity.class), 33);
                return true;
            case R.id.ajuste_pantalla:
                ajusteView.setVisibility(View.VISIBLE);
                zoom = true;
                ajustaView();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    // ----------------------------------------------------------------------------
    // Ajuste de márgenes
    // ----------------------------------------------------------------------------
    private void ajustaView() {
        int tope = Integer.parseInt(Objects.requireNonNull(pref.getString("top_margen", "0")));
        int bajo = Integer.parseInt(Objects.requireNonNull(pref.getString("bot_margen", "0")));
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        layoutParams.setMargins(0, -1 * tope, 0, -1 * bajo);
        vistaweb.setLayoutParams(layoutParams);
    }

    private void ajustaViewTop(int top) {
        int bajo = Integer.parseInt(Objects.requireNonNull(pref.getString("bot_margen", "0")));
        pref.edit().putString("top_margen", Integer.toString(top)).apply();

        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        layoutParams.setMargins(0, -1 * top, 0, -1 * bajo);
        vistaweb.setLayoutParams(layoutParams);
    }

    private void ajustaViewBot(int bot) {
        int tope = Integer.parseInt(Objects.requireNonNull(pref.getString("top_margen", "0")));
        pref.edit().putString("bot_margen", Integer.toString(bot)).apply();

        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        layoutParams.setMargins(0, -1 * tope, 0, -1 * bot);
        vistaweb.setLayoutParams(layoutParams);
    }

    // ----------------------------------------------------------------------------
    // Métodos de check de versión, etc. (opcional)
    // ----------------------------------------------------------------------------
    private void checkForUpdates() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(APP_VERSION_JSON);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    InputStream inputStream = new BufferedInputStream(connection.getInputStream());

                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder result = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append(line);
                    }

                    JSONObject response = new JSONObject(result.toString());
                    String latestVersion = response.getString("latestVersion");
                    String apkUrl = response.getString("apkUrl");
                    JSONArray changelogArray = response.getJSONArray("changelog");
                    ArrayList<String> changelog = new ArrayList<>();
                    for (int i = 0; i < changelogArray.length(); i++) {
                        changelog.add(changelogArray.getString(i));
                    }

                    if (isNewVersionAvailable(getCurrentVersion(), latestVersion)) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showUpdateDialog(apkUrl, changelog);
                            }
                        });
                    }

                    connection.disconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private String getCurrentVersion() {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return "";
        }
    }

    private boolean isNewVersionAvailable(String currentVersion, String latestVersion) {
        return !currentVersion.equals(latestVersion);
    }

    private SpannableStringBuilder getFormattedChangelog(ArrayList<String> changelog) {
        final int bulletGapWidth = 20;
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        ssb.append("Hay una nueva versión de la aplicación disponible para descargar. \n\nNovedades:\n");

        for (int i = 0; i < changelog.size(); i++) {
            String line = changelog.get(i);
            SpannableString spannableString = new SpannableString(line);
            spannableString.setSpan(new BulletSpan(bulletGapWidth), 0, line.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
            ssb.append(spannableString);

            if (i + 1 < changelog.size()) {
                ssb.append("\n\n");
            }
        }
        return ssb;
    }

    private void showUpdateDialog(String apkUrl, ArrayList<String> changelog) {
        ScrollView scrollView = new ScrollView(this);
        TextView textView = new TextView(this);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);

        int padding_h = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, getResources().getDisplayMetrics());
        int padding_v = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        textView.setPadding(padding_h, padding_v, padding_h, padding_v);

        textView.setText(getFormattedChangelog(changelog));
        scrollView.addView(textView);

        new AlertDialog.Builder(this)
                .setTitle("Actualización Disponible")
                .setView(scrollView)
                .setPositiveButton("Actualizar", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl));
                        startActivity(browserIntent);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ----------------------------------------------------------------------------
    // Helper para verificar si un String no está vacío ni nulo
    // ----------------------------------------------------------------------------
    private boolean isNotEmpty(String text) {
        return text != null && !text.trim().isEmpty();
    }

    // ----------------------------------------------------------------------------
    // Modo "desktop" en el WebView
    // ----------------------------------------------------------------------------
    public void setDesktopMode(WebView webView, boolean enabled) {
        String newUserAgent = webView.getSettings().getUserAgentString();
        if (enabled) {
            try {
                String ua = webView.getSettings().getUserAgentString();
                String androidOSString = ua.substring(ua.indexOf("("), ua.indexOf(")") + 1);
                newUserAgent = ua.replace(androidOSString, "(X11; Linux x86_64)");
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            newUserAgent = null;
        }

        webView.getSettings().setUserAgentString(newUserAgent);
        webView.getSettings().setUseWideViewPort(enabled);
        webView.getSettings().setLoadWithOverviewMode(enabled);
        webView.reload();
    }
}
