package zelytra.librarius.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * The display preferences an account carries from one device to the next (#613).
 *
 * <p>Stored whole in {@code app_user.preferences} (JSONB). Every field is optional: {@code null}
 * means the member never chose, and each device keeps its own default for it. The values are
 * the web app's identifiers, checked at the API's edge by {@code PreferencesDto}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserPreferences(String theme, List<String> bottomBar, String libraryKind,
        String sheetTheme) {

    /** Nothing chosen. */
    public static final UserPreferences NONE = new UserPreferences(null, null, null, null);

    /** These preferences with every field {@code patch} sets replacing the stored one. */
    public UserPreferences merge(UserPreferences patch) {
        return new UserPreferences(
                patch.theme != null ? patch.theme : theme,
                patch.bottomBar != null ? List.copyOf(patch.bottomBar) : bottomBar,
                patch.libraryKind != null ? patch.libraryKind : libraryKind,
                patch.sheetTheme != null ? patch.sheetTheme : sheetTheme);
    }
}
