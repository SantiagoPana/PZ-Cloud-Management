package pzcloud;

import java.nio.file.*;
import java.util.*;

/** Language tests without opening windows, Google OAuth or accessing real saves. */
public final class I18nSelfTest {
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Path home=Files.createTempDirectory("knoxnet-language-");
        Locale original=Locale.getDefault();
        try {
            Config config=new Config(home);
            check(config.get("language").equals("auto"),"Existing setups should default to automatic");
            Locale.setDefault(Locale.forLanguageTag("es-AR"));
            I18n.configure(config);
            check(I18n.language().equals("es"),"Spanish (Argentina) must be detected");
            check(I18n.t("Guardar partida").equals("Guardar partida"),"Spanish text mismatch");
            Locale.setDefault(Locale.US);
            check(I18n.language().equals("en"),"English Windows locale must be detected");
            check(I18n.t("Guardar partida").equals("Back up save"),"English text mismatch");
            Locale.setDefault(Locale.GERMANY);
            check(I18n.language().equals("en"),"Unsupported OS language should fall back to English");
            I18n.change(config,"es");
            check(I18n.language().equals("es"),"Manual Spanish must override system locale");
            I18n.change(config,"en");
            check(I18n.language().equals("en"),"Manual English must override system locale");
            check(new Config(home).get("language").equals("en"),"Manual selection must survive restarting");
            I18n.configure(new Config(home));
            check(I18n.t("Cancelar").equals("Cancel"),"Sign-in translation unavailable");
            I18n.change(config,"auto");
            Locale.setDefault(Locale.forLanguageTag("es-MX"));
            check(I18n.language().equals("es"),"Automatic language must update when OS locale changes");
            check(new Config(home).get("language").equals("auto"),"Automatic setting was not persisted");
            check(I18n.normalize("invalid").equals("auto"),"Unexpected locale values must fall back safely");
            System.out.println("Locale passed: es-AR/es-MX, en-US, other-OS fallback, manual override, persistence and sign-in text");
        } finally { Locale.setDefault(original); FilesEx.deleteTree(home); }
    }
}
