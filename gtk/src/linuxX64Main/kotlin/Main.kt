package com.serebit.wraith.gtk

import com.serebit.wraith.core.*
import gtk3.*
import kotlinx.cinterop.*
import kotlin.system.exitProcess

val result = obtainWraithPrism()
val wraith: WraithPrism get() = (result as? WraithPrismResult.Success)!!.device

@OptIn(ExperimentalUnsignedTypes::class)
fun CPointer<GtkApplication>.activate() {
    val activeWindow = gtk_application_get_active_window(this)
    if (activeWindow == null) {
        val windowWidget = gtk_application_window_new(this)!!

        val logoWidgets = LogoWidgets()
        val fanWidgets = FanWidgets()
        val ringWidgets = RingWidgets()

        // unset focus on left click with mouse button
        windowWidget.connectSignal(
            "button-press-event",
            staticCFunction<Widget, CPointer<GdkEventButton>, Unit> { it, event ->
                if (event.pointed.type == GDK_BUTTON_PRESS && event.pointed.button == 1u) {
                    gtk_window_set_focus(it.reinterpret(), null)
                    gtk_window_set_focus_visible(it.reinterpret(), 0)
                }
            })

        val window = windowWidget.reinterpret<GtkWindow>()
        gtk_window_set_title(window, "Wraith Master")
        gtk_window_set_default_size(window, 480, -1)
        gtk_window_set_default_icon_name("applications-games")
        gtk_window_set_icon_name(window, "wraith-master")

        val box = gtk_box_new(GtkOrientation.GTK_ORIENTATION_VERTICAL, 0)!!
        gtk_container_add(window.reinterpret(), box)

        val mainNotebook = gtk_notebook_new()!!
        gtk_container_add(box.reinterpret(), mainNotebook)

        val logoGrid = mainNotebook.newSettingsPage("Logo").newSettingsGrid()
        val fanGrid = mainNotebook.newSettingsPage("Fan").newSettingsGrid()
        val ringGrid = mainNotebook.newSettingsPage("Ring").newSettingsGrid()

        fun Widget?.gridLabel(position: Int, text: String) = gtk_label_new(text)?.apply {
            gtk_widget_set_halign(this, GtkAlign.GTK_ALIGN_START)
            gtk_widget_set_hexpand(this, 1)
            gtk_widget_set_size_request(this, -1, 36)
            gtk_grid_attach(this@gridLabel?.reinterpret(), this, 0, position, 1, 1)
        }

        listOf(logoGrid, fanGrid, ringGrid).forEach {
            it.gridLabel(0, "Mode")
            it.gridLabel(1, "Color")
            it.gridLabel(2, "Brightness")
            it.gridLabel(3, "Speed")
        }
        fanGrid.gridLabel(4, "Mirage")
        ringGrid.gridLabel(4, "Rotation Direction")
        ringGrid.gridLabel(5, "Morse Text")

        fun ComponentWidgets<*>.attachWidgetsToGrid(grid: Widget) {
            widgets.forEachIndexed { i, it -> grid.gridAttachRight(it, i) }
        }

        logoWidgets.attachWidgetsToGrid(logoGrid)
        fanWidgets.attachWidgetsToGrid(fanGrid)
        ringWidgets.attachWidgetsToGrid(ringGrid)

        val saveOptionBox = gtk_button_box_new(GtkOrientation.GTK_ORIENTATION_HORIZONTAL)?.apply {
            gtk_container_add(box.reinterpret(), this)
            gtk_container_set_border_width(reinterpret(), 12u)
            gtk_button_box_set_layout(reinterpret(), GTK_BUTTONBOX_END)
            gtk_box_set_spacing(reinterpret(), 8)
            gtk_box_set_child_packing(box.reinterpret(), this, 0, 1, 0u, GtkPackType.GTK_PACK_END)
        }

        gtk_button_new()?.apply {
            data class AllWidgets(val logo: LogoWidgets, val fan: FanWidgets, val ring: RingWidgets)

            gtk_button_set_label(reinterpret(), "Reset")
            val widgets = StableRef.create(AllWidgets(logoWidgets, fanWidgets, ringWidgets)).asCPointer()
            connectSignalWithData("clicked", widgets, staticCFunction<Widget, COpaquePointer, Unit> { it, ptr ->
                val ref = ptr.asStableRef<AllWidgets>()
                val (logo, fan, ring) = ref.get()
                wraith.reset()
                logo.fullReload(); fan.fullReload(); ring.fullReload()
                gtk_combo_box_set_active(logo.modeBox.reinterpret(), logo.component.mode.index)
                gtk_combo_box_set_active(logo.modeBox.reinterpret(), fan.component.mode.index)
                ref.dispose()
            })
            gtk_container_add(saveOptionBox?.reinterpret(), this)
        }

        gtk_button_new()?.apply {
            gtk_button_set_label(reinterpret(), "Save")
            gtk_style_context_add_class(gtk_widget_get_style_context(this), "suggested-action")
            connectSignal("clicked", staticCFunction<Widget, Unit> { wraith.save(); Unit })
            gtk_container_add(saveOptionBox?.reinterpret(), this)
        }

        gtk_widget_show_all(windowWidget)
    } else {
        gtk_message_dialog_new(
            activeWindow, 0u, GtkMessageType.GTK_MESSAGE_INFO, GtkButtonsType.GTK_BUTTONS_OK, "%s",
            "Cannot open extra Wraith Master windows."
        )?.let {
            gtk_dialog_run(it.reinterpret())
            gtk_widget_destroy(it)
        }
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
fun main(args: Array<String>) {
    val app = gtk_application_new("com.serebit.wraith", G_APPLICATION_FLAGS_NONE)!!
    val status: Int

    app.connectSignal("activate", when (result) {
        is WraithPrismResult.Success -> staticCFunction<CPointer<GtkApplication>, Unit> { it.activate() }
        is WraithPrismResult.Failure -> staticCFunction<CPointer<GtkApplication>, Unit> {
            val dialog = gtk_message_dialog_new(
                null, 0u, GtkMessageType.GTK_MESSAGE_ERROR, GtkButtonsType.GTK_BUTTONS_OK, "%s", result.message
            )

            gtk_dialog_run(dialog?.reinterpret())
        }
    })

    status = memScoped { g_application_run(app.reinterpret(), args.size, args.map { it.cstr.ptr }.toCValues()) }
    if (result is WraithPrismResult.Success) wraith.close()

    g_object_unref(app)
    if (status != 0) exitProcess(status)
}
