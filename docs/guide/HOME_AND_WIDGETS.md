# Home, App Library and widgets

[Documentation](../README.md) · [Themes](THEMES.md) · [Screenshots](../screenshots/README.md)

## Arrange apps

Hold an app to open its actions. Start moving it to enter Home editing, or choose
**Edit Home Screen**. Once editing, drag without another long press. Neighbouring
icons make room before release; the saved order changes when you drop. A cancelled
gesture restores the saved arrangement.

Pause near an edge to move between Home pages. The four-slot Dock accepts apps;
dropping on an occupied slot exchanges its app with the Home shortcut. Android
widgets reserve their own cells, and apps flow around them. Home uses four columns
and fits artwork to the available width and font scale.

To make a folder, hold a dragged app over another app or folder until its outline
brightens, then release. Releasing before the grouping delay reorders instead.
**Create Folder** in edit mode also supports selecting several apps. Folder
actions let you rename or dissolve it without uninstalling its apps.

Removing a Home shortcut does not uninstall the application. Confirmed Android
uninstalls clear its shortcuts from Home, Dock, folders and suggestions; app
updates preserve placement. Search and Library still show installed apps whose
Home shortcuts you removed.

## Size and artwork

**Settings → Home Screen** offers 40–100 dp icons. An open folder uses the actual
Home artwork size. **Settings → App Library** follows Home by default or uses an
independent 40–100 dp preference; expanded categories adapt their column count.
Small category thumbnails remain previews, rather than full-size icons.

Hold an app and choose **More → Change Icon** to assign artwork from the current
theme or an installed pack. For example, give your preferred gallery the Photos
icon. See [icon packs and overrides](THEMES.md#icon-packs-and-per-app-overrides).

## Add Android widgets

1. Enter Home editing and tap **Add Widgets**, or open **Settings → Widgets**.
2. Choose **Home Screen** or **Today**, and a Home size.
3. Select the provider and tap **Add**. Accept Android's binding/configuration
   screen when the provider requires one.
4. Use the widget's **…** menu to resize, move, configure or remove it.

In edit mode, drag a Home widget and pause at the edge to change pages. A trailing
page is available during a widget drag. Moving or resizing preserves the widget
binding; removing releases it. Provider reconfiguration appears only when supported.

Up to eight native widgets can be hosted. Large provider layouts fit proportionally
inside clipped containers with padding. Catalog previews are passive and do not
bind widgets. The provider controls its content, available sizes and network use.

## Built-in cards and Today

Clock and Calendar use local time/date and can launch your chosen installed apps.
Home settings toggle them independently; their edit actions support repositioning.
In **Today → Edit**, use handles or arrows to reorder cards, remove built-in
sections, restore them, or move the built-in clock/calendar section to Home.
Removing a Today card does not disable its Home counterpart.

Today can instead display [news](NEWS_AND_UPDATES.md). Switching modes preserves
the widget configuration. Widget IDs and bindings are installation-local and do
not travel in layout backups.
