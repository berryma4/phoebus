/*******************************************************************************
 * Copyright (c) 2017 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.csstudio.display.builder.editor.app;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.csstudio.display.builder.editor.EditorGUI;
import org.csstudio.display.builder.editor.EditorUtil;
import org.csstudio.display.builder.model.DisplayModel;
import org.csstudio.display.builder.model.ModelPlugin;
import org.csstudio.display.builder.model.Widget;
import org.csstudio.display.builder.model.WidgetPropertyListener;
import org.csstudio.display.builder.model.persist.WidgetClassesService;
import org.csstudio.display.builder.model.util.ModelResourceUtil;
import org.csstudio.display.builder.model.util.ModelThreadPool;
import org.csstudio.display.builder.model.widgets.EmbeddedDisplayWidget;
import org.csstudio.display.builder.model.widgets.GroupWidget;
import org.csstudio.display.builder.representation.javafx.FilenameSupport;
import org.csstudio.display.builder.representation.javafx.JFXRepresentation;
import org.phoebus.framework.jobs.JobMonitor;
import org.phoebus.framework.persistence.Memento;
import org.phoebus.framework.spi.AppDescriptor;
import org.phoebus.framework.spi.AppInstance;
import org.phoebus.framework.spi.AppResourceDescriptor;
import org.phoebus.framework.util.ResourceParser;
import org.phoebus.ui.dialog.DialogHelper;
import org.phoebus.ui.docking.DockItemWithInput;
import org.phoebus.ui.docking.DockPane;
import org.phoebus.ui.javafx.ToolbarHelper;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.MenuItem;

/** Display Editor Instance
 *  @author Kay Kasemir
 */
@SuppressWarnings("nls")
public class DisplayEditorInstance implements AppInstance
{
    /** Memento tags */
    private static final String LEFT_DIVIDER = "left_divider",
                                RIGHT_DIVIDER = "right_divider";
    private final AppResourceDescriptor app;
    private DockItemWithInput dock_item;
    private final EditorGUI editor_gui;

    private final WidgetPropertyListener<String> model_name_listener = (property, old_value, new_value) ->
        Platform.runLater(() -> dock_item.setLabel(property.getValue()));

    /** Last time the file was modified */
    private volatile FileTime modification_marker = null;

    DisplayEditorInstance(final DisplayEditorApplication app)
    {
        this.app = app;

        final DockPane dock_pane = DockPane.getActiveDockPane();
        JFXRepresentation.setSceneStyle(dock_pane.getScene());
        EditorUtil.setSceneStyle(dock_pane.getScene());

        editor_gui = new EditorGUI();

        extendToolbar();

        dock_item = new DockItemWithInput(this, editor_gui.getParentNode(), null, FilenameSupport.file_extensions, this::doSave);
        dock_pane.addTab(dock_item);

        // Update tab's title when model has been loaded
        editor_gui.setModelListener(this::handleNewModel);

        // Mark 'dirty' whenever there's a change, i.e. something to un-do
        editor_gui.getDisplayEditor()
                  .getUndoableActionManager()
                  .addListener((to_undo, to_redo) -> dock_item.setDirty(to_undo != null));

        final ContextMenu menu = new ContextMenu();
        final Control menu_node = editor_gui.getDisplayEditor().getContextMenuNode();
        menu_node.setOnContextMenuRequested(event -> handleContextMenu(menu));
        menu_node.setContextMenu(menu);
    }

    private void extendToolbar()
    {
        final ObservableList<Node> toolbar = editor_gui.getDisplayEditor().getToolBar().getItems();
        toolbar.add(ToolbarHelper.createSpring());
        toolbar.add(ExecuteDisplayAction.asButton(this));
    }

    private void handleContextMenu(final ContextMenu menu)
    {
        final ObservableList<MenuItem> items = menu.getItems();
        items.setAll(ExecuteDisplayAction.asMenuItem(this));

        // Depending on number of selected widgets,
        // allow grouping, ungrouping, morphing
        final List<Widget> selection = editor_gui.getDisplayEditor().getWidgetSelectionHandler().getSelection();
        // OK to create (resp. 'start') a group with just one widget.
        // Even better when there's more than one widget.
        if (selection.size() >= 1)
            items.add(new CreateGroupAction(editor_gui.getDisplayEditor(), selection));

        if (selection.size() == 1  &&  selection.get(0) instanceof GroupWidget)
            items.add(new RemoveGroupAction(editor_gui.getDisplayEditor(), (GroupWidget)selection.get(0)));

        if (selection.size() == 1  &&  selection.get(0) instanceof EmbeddedDisplayWidget)
            items.add(new EditEmbeddedDisplayAction(app, (EmbeddedDisplayWidget)selection.get(0)));

        if (selection.size() > 0)
            items.add(new MorphWidgetsMenu(editor_gui.getDisplayEditor()));

        items.add(new ReloadDisplayAction(this));

        final DisplayModel model = editor_gui.getDisplayEditor().getModel();
        if (model != null  &&  !model.isClassModel())
        {
            items.add(new ReloadClassesAction(this));

            // No widgets selected: Add actions for just the model
            if (selection.isEmpty())
                items.add(new SetDisplaySize(editor_gui.getDisplayEditor()));
        }
    }

    @Override
    public AppDescriptor getAppDescriptor()
    {
        return app;
    }

    /** Select dock item, make visible */
    void raise()
    {
        dock_item.select();
    }

    @Override
    public void restore(final Memento memento)
    {
        final Optional<Number> left = memento.getNumber(LEFT_DIVIDER);
        final Optional<Number> right = memento.getNumber(RIGHT_DIVIDER);
        if (left.isPresent()  &&  right.isPresent())
            editor_gui.setDividerPositions(left.get().doubleValue(),
                                           right.get().doubleValue());
    }

    @Override
    public void save(final Memento memento)
    {
        final double[] dividers = editor_gui.getDividerPositions();
        if (dividers.length != 2)
            throw new IllegalStateException("Expect left, right");
        memento.setNumber(LEFT_DIVIDER, dividers[0]);
        memento.setNumber(RIGHT_DIVIDER, dividers[1]);
    }

    EditorGUI getEditorGUI()
    {
        return editor_gui;
    }

    void loadDisplay(final URI resource)
    {
        final DisplayModel old_model = editor_gui.getDisplayEditor().getModel();
        if (old_model != null)
            old_model.propName().removePropertyListener(model_name_listener);

        // Set input ASAP to prevent opening another instance for same input
        dock_item.setInput(resource);

        final File file = new File(resource);
        try
        {
            modification_marker = Files.getLastModifiedTime(file.toPath());
        }
        catch (IOException ex)
        {
            modification_marker = null;
        }
        editor_gui.loadModel(file);

        // New model is now loaded in background thread,
        // and handleNewModel will be invoked when done
    }

    private void handleNewModel(final DisplayModel model)
    {
        model.propName().addPropertyListener(model_name_listener);
        model_name_listener.propertyChanged(model.propName(), null, null);
    }

    void reloadDisplay()
    {
        loadDisplay(dock_item.getInput());
    }

    void loadWidgetClasses()
    {
        // Trigger re-load of classes
        ModelPlugin.reloadConfigurationFiles();
        // On separate thread..
        ModelThreadPool.getExecutor().execute(() ->
        {
            // get widget classes and apply to model
            final DisplayModel model = editor_gui.getDisplayEditor().getModel();
            if (model != null)
                WidgetClassesService.getWidgetClasses().apply(model);
        });
    }

    void doSave(final JobMonitor monitor) throws Exception
    {
        final URI orig_input = dock_item.getInput();
        final File file = Objects.requireNonNull(ResourceParser.getFile(orig_input));

        final File proper = ModelResourceUtil.enforceFileExtension(file, DisplayModel.FILE_EXTENSION);
        if (file.equals(proper))
        {
            // Check if file has been changed outside of this editor
            final FileTime as_loaded = modification_marker;
            if (as_loaded != null  &&  file.exists()  &&  file.canRead())
            {
                final FileTime current = Files.getLastModifiedTime(file.toPath());
                if (! current.equals(as_loaded))
                {
                    final CompletableFuture<ButtonType> response = new CompletableFuture<>();
                    // Prompt on UI thread
                    Platform.runLater(() ->
                    {
                        final Alert prompt = new Alert(AlertType.CONFIRMATION);
                        prompt.setTitle("File has changed");
                        prompt.setResizable(true);
                        prompt.setHeaderText(
                            "The file\n   " + file.toString() + "\n" +
                            "has been changed while you were editing it.\n\n" +
                            "'OK' to save and thus overwrite what somebody else has written,\n" +
                            "or\n" +
                            "'Cancel' and then re-load the file or save it under a different name.");
                        DialogHelper.positionDialog(prompt, dock_item.getTabPane(), -200, -200);
                        response.complete(prompt.showAndWait().orElse(ButtonType.CANCEL));
                    });

                    // If user doesn't want to overwrite, abort the save
                    if (response.get() != ButtonType.OK)
                        return;
                }
            }

            editor_gui.saveModelAs(file);
            modification_marker = Files.getLastModifiedTime(file.toPath());
        }
        else
        {   // Save-As with proper file name
            dock_item.setInput(proper.toURI());
            if (! dock_item.save_as(monitor))
                dock_item.setInput(orig_input);
        }
    }
}
