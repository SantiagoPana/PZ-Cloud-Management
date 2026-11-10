package pzcloud;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.io.IOException;
import java.util.*;

/** User-interface language: auto detects the OS, but a saved override takes precedence. */
final class I18n {
    private static final Map<String,String> EN;
    private static final Map<String,String> ES;
    private static volatile String preference="auto";
    private I18n() {}
    static {
        Map<String,String> translation=new LinkedHashMap<>();
        translation.put("En esta PC", "On this PC");
        translation.put("En mi carpeta / nube", "My folder / cloud");
        translation.put("Elegí una partida", "Select a save");
        translation.put("Listo", "Ready");
        translation.put("Guardar automáticamente al cerrar Zomboid", "Back up automatically when Zomboid closes");
        translation.put("Elegí dónde buscar tus copias.", "Choose where to find your backups.");
        translation.put(" · Google incluido", " · Google included");
        translation.put(" · Solo copias locales", " · Local backups only");
        translation.put("Tus partidas, a salvo. Seguí jugando donde quieras.", "Your saves, protected. Keep playing anywhere.");
        translation.put("Iniciar sesión con Google", "Sign in with Google");
        translation.put("Configuración", "Settings");
        translation.put("Mis partidas", "My saves");
        translation.put("Recuperar partida", "Restore save");
        translation.put("Minimizar a la bandeja", "Minimize to tray");
        translation.put("Knox Net sigue funcionando; abrilo desde el icono junto al reloj.", "Knox Net keeps running; open it from the icon near the clock.");
        translation.put("Ver actividad", "View activity");
        translation.put("Actividad", "Activity");
        translation.put("Guardando preferencia…", "Saving preference…");
        translation.put("Buscando tus partidas…", "Finding your saves…");
        translation.put(" · Guardado automático", " · Automatic backup");
        translation.put(" · Guardado manual", " · Manual backup");
        translation.put("Buscar partida", "Search saves");
        translation.put("Filtra por nombre o modo sin volver a leer tus partidas.", "Filter by name or mode without rereading your saves.");
        translation.put("Guardar partida", "Back up save");
        translation.put("Jugar", "Play");
        translation.put("Preparando Zomboid…", "Preparing Zomboid…");
        translation.put("Enviar pendientes", "Send pending");
        translation.put("La copia automática espera a que cierres el juego por completo.", "Automatic backups wait until the game closes completely.");
        translation.put("Versiones anteriores…", "Previous versions…");
        translation.put("Actualizar partidas", "Refresh saves");
        translation.put("Actualizando…", "Refreshing…");
        translation.put("Tu progreso actual se respalda antes de recuperar otra copia.", "Your current progress is backed up before restoring another copy.");
        translation.put("Buscar copias:", "Find backups:");
        translation.put("Actualizar lista", "Refresh list");
        translation.put("Recuperar esta partida", "Restore this save");
        translation.put("Elegí la copia que querés recuperar.", "Select a backup to restore.");
        translation.put("Trayendo la copia a esta PC…", "Downloading backup to this PC…");
        translation.put("Más opciones…", "More options…");
        translation.put("Importar copia…", "Import backup…");
        translation.put("Primero elegí una partida. Si no aparece, revisá la carpeta del juego en Configuración.", "Select a save first. If it is missing, check the game folder in Settings.");
        translation.put("Guardando y verificando tu partida…", "Backing up and verifying your save…");
        translation.put("Copia local lista. Se enviará cuando el juego esté cerrado.", "Local backup ready. It will upload after the game closes.");
        translation.put("Copia local lista. Envío pendiente: iniciá sesión con Google.", "Local backup ready. Upload pending: sign in with Google.");
        translation.put("Copia guardada en esta PC.", "Backup saved on this PC.");
        translation.put("Enviando tus copias pendientes…", "Sending pending backups…");
        translation.put("Tus copias terminaron de enviarse al destino elegido.", "All pending backups were sent to the selected destination.");
        translation.put("Quedan copias pendientes; se reintentará automáticamente.", "Some backups are still pending; retrying automatically.");
        translation.put(" partidas", " saves");
        translation.put(" de ", " of ");
        translation.put("Todavía no hay partidas", "No saves found yet");
        translation.put("Sin coincidencias", "No matches");
        translation.put("Jugá una vez o recuperá una copia de otra PC.", "Play once or restore a backup from another PC.");
        translation.put("Probá con otro nombre o borrá la búsqueda.", "Try a different name or clear the search.");
        translation.put("Última copia: ", "Last backup: ");
        translation.put("Todavía no hiciste una copia de esta partida.", "No backup has been made for this save yet.");
        translation.put("Destino: esta PC y tu carpeta sincronizada", "Destination: this PC and your synced folder");
        translation.put("Destino: esta PC y Google Drive", "Destination: this PC and Google Drive");
        translation.put("Destino: esta PC · conectá Google para enviar", "Destination: this PC · connect Google to upload");
        translation.put("Destino: esta PC", "Destination: this PC");
        translation.put(" copias guardadas · ", " backups saved · ");
        translation.put(" por enviar", " to send");
        translation.put("en Google Drive", "on Google Drive");
        translation.put("en tu carpeta", "in your folder");
        translation.put("pendiente de enviar", "pending upload");
        translation.put("en esta PC", "on this PC");
        translation.put("Conectá Google o elegí una carpeta sincronizada en Configuración.", "Connect Google or select a synced folder in Settings.");
        translation.put("La nube todavía no está conectada. Tus copias locales siguen disponibles.", "Cloud storage is not connected yet. Your local backups are still available.");
        translation.put("Buscando tus copias afuera…", "Looking for cloud backups…");
        translation.put("Leyendo tus copias…", "Reading your backups…");
        translation.put("Todavía no hay copias en este lugar.", "There are no backups here yet.");
        translation.put(" copias · Más recientes primero", " backups · Newest first");
        translation.put(" (recuperada)", " (restored)");
        translation.put("Recuperar «", "Restore “");
        translation.put("Copia del ", "Backup from ");
        translation.put("Nombre en esta PC:", "Name on this PC:");
        translation.put("Usá un nombre nuevo para conservar ambas partidas.", "Use a new name to keep both saves.");
        translation.put("Si el nombre ya existe, primero se respalda su progreso.", "If the name already exists, its progress is backed up first.");
        translation.put("Necesitás la misma versión de PZ y los mismos mods.", "You need the same PZ version and mods.");
        translation.put("Recuperando tu partida…", "Restoring your save…");
        translation.put("Listo. Abrí Zomboid y buscá «", "Done. Open Zomboid and find “");
        translation.put("» en Cargar partida.", "” under Load Game.");
        translation.put("Solo en esta PC", "Only on this PC");
        translation.put("En una carpeta de Drive u OneDrive que ya uso", "In a Drive or OneDrive folder I already use");
        translation.put("Google Drive directo", "Direct Google Drive");
        translation.put("Elegir carpeta…", "Choose folder…");
        translation.put("¿Dónde querés guardar tus copias?", "Where do you want to store your backups?");
        translation.put("Elegí una carpeta que tu aplicación de nube ya sincronice.", "Choose a folder already synced by your cloud app.");
        translation.put("Esperá a que esa aplicación confirme la subida antes de cambiar de PC.", "Wait until that app confirms the upload before switching PCs.");
        translation.put("Iniciá sesión en Google y autorizá tus copias. La conexión se recuerda en esta PC.", "Sign in with Google and authorize backups. Your session is remembered on this PC.");
        translation.put("Al recuperar en otra PC, elegí allí la misma carpeta o cuenta.", "On the other PC, select the same folder or account.");
        translation.put("Configuración sencilla", "Quick setup");
        translation.put("Elegí la carpeta donde querés guardar las copias.", "Choose a folder to store your backups.");
        translation.put("Preparando tus copias…", "Preparing your backups…");
        translation.put("Conectá Google Drive desde Configuración para enviar tus copias.", "Connect Google Drive in Settings to upload backups.");
        translation.put("Activando tu conexión…", "Activating your connection…");
        translation.put("Google Drive comprobado. Ya podés guardar y recuperar tus partidas.", "Google Drive verified. You can now back up and restore saves.");
        translation.put("Estas opciones no hacen falta para guardar y recuperar partidas.", "These options are not needed for normal backups and restores.");
        translation.put("Cambiar carpeta Zomboid…", "Change Zomboid folder…");
        translation.put("Cambiando carpeta…", "Changing folder…");
        translation.put("Carpeta actualizada. Reiniciá el gestor para actualizar el botón dentro del juego.", "Folder updated. Restart the manager to update the in-game button.");
        translation.put("Instalar botón en el juego", "Install in-game button");
        translation.put("Instalando…", "Installing…");
        translation.put("Activá Knox Net en el menú Mods de Zomboid.", "Enable Knox Net in Zomboid's Mods menu.");
        translation.put("Abrir mis copias", "Open my backups");
        translation.put("Ayuda de Google Drive", "Google Drive help");
        translation.put("Desconectar Google", "Disconnect Google");
        translation.put("Las copias remotas se conservan. Las nuevas subidas irán a la próxima cuenta conectada.", "Cloud backups are preserved. New uploads will use the next connected account.");
        translation.put("Desconectar", "Disconnect");
        translation.put("Desconectando…", "Disconnecting…");
        translation.put("Cerrar el gestor por completo", "Exit manager completely");
        translation.put("Más opciones", "More options");
        translation.put("Copia de Knox Net", "Knox Net backup");
        translation.put("Verificando tu copia…", "Verifying your backup…");
        translation.put("Elegí una copia primero.", "Select a backup first.");
        translation.put("Exportar ZIP", "Export ZIP");
        translation.put("Eliminar copia", "Delete backup");
        translation.put("Cancelar", "Cancel");
        translation.put("Aceptar", "OK");
        translation.put("Abrir", "Open");
        translation.put("Eliminar copia remota", "Delete cloud backup");
        translation.put("La partida actual no se elimina con estas opciones.", "These options do not delete your current save.");
        translation.put("Más opciones de copia", "More backup options");
        translation.put("El archivo existe. ¿Reemplazarlo?", "File already exists. Replace it?");
        translation.put("Exportar copia", "Export backup");
        translation.put("Exportando copia…", "Exporting backup…");
        translation.put("Copia exportada.", "Backup exported.");
        translation.put("¿Eliminar definitivamente esta copia?", "Permanently delete this backup?");
        translation.put("Eliminando copia…", "Deleting backup…");
        translation.put("No se completó: ", "Failed: ");
        translation.put("No se completó la operación", "Operation failed");
        translation.put("Carpeta sincronizada", "Synced folder");
        translation.put("Google Drive conectado", "Google Drive connected");
        translation.put("Google Drive sin conectar", "Google Drive disconnected");
        translation.put("Copias en esta PC", "Local backups");
        translation.put(" pendientes", " pending");
        translation.put("Mi conexión de Google", "My Google connection");
        translation.put("Abrí esta dirección en tu navegador:", "Open this address in your browser:");
        translation.put("Sigue abierto en la bandeja. Hacé clic en el icono para volver, o elegí Salir para cerrarlo.", "Knox Net is running in the tray. Click its icon to return, or choose Exit to quit.");
        translation.put("Esperá a que termine la operación antes de cerrar.", "Wait for the operation to finish before closing.");
        translation.put("Esperá a que termine la operación antes de salir.", "Wait for the operation to finish before exiting.");
        translation.put("Abrir Knox Net", "Open Knox Net");
        translation.put("Salir", "Exit");
        translation.put("No hay un navegador predeterminado.", "No default browser is available.");
        translation.put("Conectar Google Drive", "Connect Google Drive");
        translation.put("Conectá tu cuenta de Google", "Connect your Google account");
        translation.put("Elegí tu cuenta en Google y autorizá el acceso a tus copias. Al terminar, volvés automáticamente a Knox Net.", "Choose your Google account and authorize backup access. You will return automatically to Knox Net.");
        translation.put("Información de Knox Net", "About Knox Net");
        translation.put("Información de esta instalación", "Installation details");
        translation.put("Enlace copiado. Abrilo en tu navegador y elegí tu cuenta.", "Link copied. Open it in your browser and choose your account.");
        translation.put("Cerrar", "Close");
        translation.put("Copiar enlace", "Copy link");
        translation.put("Elegir otra cuenta", "Choose another account");
        translation.put("Usar esta conexión", "Use this connection");
        translation.put("Tu sesión está guardada. Al usarla, comprobamos que Google Drive responda.", "Your session is saved. We will check that Google Drive responds before using it.");
        translation.put("Descargá la versión actualizada de Knox Net y volvé a intentar. Tus partidas y copias locales se conservan.", "Download the latest Knox Net and try again. Your saves and local backups are preserved.");
        translation.put("La sesión se guarda para que no tengas que repetir este paso.", "Your session is saved so you do not need to repeat this step.");
        translation.put("Código ", "Code ");
        translation.put(". Revisá Información de esta instalación para identificar la edición que se abrió.", ". Check Installation details to identify the edition that opened.");
        translation.put("Preparando conexión…", "Preparing connection…");
        translation.put("Preparando el inicio de sesión…", "Preparing sign-in…");
        translation.put("Volver a abrir Google", "Reopen Google");
        translation.put("Terminá de iniciar sesión en la pantalla de Google. Esta ventana se cierra al conectar.", "Finish signing in on Google's page. This window will close when connected.");
        translation.put("Google respondió. Terminando la conexión…", "Google responded. Finishing connection…");
        translation.put("Conectando…", "Connecting…");
        translation.put("Guardando tu conexión en esta PC…", "Saving your connection on this PC…");
        translation.put("Comprobando Drive…", "Checking Drive…");
        translation.put("Comprobando que tu cuenta permita acceder a los respaldos de Knox Net…", "Checking that your account can access Knox Net backups…");
        translation.put("No se pudo conectar. Intentá otra vez.", "Could not connect. Try again.");
        translation.put("No se pudo abrir el navegador. Podés copiar el enlace y abrirlo manualmente para continuar.", "Could not open the browser. Copy the link and open it manually to continue.");
        translation.put("La conexión ya se está guardando. Falta un instante…", "Your connection is being saved. Almost done…");
        translation.put("Idioma / Language", "Idioma / Language");
        translation.put("Idioma de la aplicación:", "Application language:");
        translation.put("Automático (idioma del sistema)", "Automatic (system language)");
        translation.put("Español", "Español");
        translation.put("English", "English");
        translation.put("Cambiar idioma", "Change language");
        translation.put("Elegí el idioma de Knox Net:", "Choose Knox Net's language:");
        translation.put("Idioma actualizado.", "Language updated.");
        translation.put("No se pudo guardar el idioma.", "Could not save language preference.");
        EN=Collections.unmodifiableMap(translation);
        Map<String,String> reverse=new LinkedHashMap<>();
        translation.forEach((es,en)->reverse.putIfAbsent(en,es));
        ES=Collections.unmodifiableMap(reverse);
    }
    static String normalize(String choice) { return "en".equals(choice)||"es".equals(choice)?choice:"auto"; }
    static String resolve(String choice,Locale system) {
        String fixed=normalize(choice);
        return !"auto".equals(fixed)?fixed:("es".equalsIgnoreCase(system.getLanguage())?"es":"en");
    }
    static void configure(Config config) { preference=normalize(config.get("language")); }
    static String preference() { return preference; }
    static String language() { return resolve(preference,Locale.getDefault()); }
    static void change(Config config,String choice) throws IOException {
        String normalized=normalize(choice);
        config.set("language",normalized); // Existing backups and OAuth session remain untouched.
        preference=normalized;
    }
    static String t(String text) {
        if(text==null) return null;
        return ("en".equals(language())?EN:ES).getOrDefault(text,text);
    }
    static void localize(Component c) {
        if(c instanceof JLabel l) l.setText(t(l.getText()));
        else if(c instanceof AbstractButton b) b.setText(t(b.getText()));
        else if(c instanceof JTextArea a && !a.isEditable() && !"activity-log".equals(a.getName())) a.setText(t(a.getText()));
        if(c instanceof JTabbedPane pane)
            for(int i=0;i<pane.getTabCount();i++) pane.setTitleAt(i,t(pane.getTitleAt(i)));
        if(c instanceof Container container)
            for(Component child:container.getComponents()) localize(child);
    }
    static void localizeDialog(Window window) {
        if(window instanceof Dialog d) d.setTitle(t(d.getTitle()));
        localize(window);
    }
    static void installSourceRenderer(JComboBox<String> combo) {
        combo.setRenderer((list,value,index,selected,focus)->{
            JLabel label=(JLabel)new DefaultListCellRenderer().getListCellRendererComponent(list,t(value),index,selected,focus);
            return label;
        });
    }
    static void configureOptionPane() {
        UIManager.put("OptionPane.okButtonText",t("Aceptar"));
        UIManager.put("OptionPane.cancelButtonText",t("Cancelar"));
        UIManager.put("FileChooser.openDialogTitleText",t("Abrir"));
        UIManager.put("FileChooser.cancelButtonText",t("Cancelar"));
    }
}
