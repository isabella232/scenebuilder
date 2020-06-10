// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.sceneBuilder;

import com.oracle.javafx.scenebuilder.kit.editor.EditorController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.content.ContentPanelController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.hierarchy.treeview.HierarchyTreeViewController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.inspector.InspectorPanelController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.library.LibraryPanelController;
import com.oracle.javafx.scenebuilder.kit.library.BuiltinLibrary;
import com.oracle.javafx.scenebuilder.kit.library.Library;
import com.oracle.javafx.scenebuilder.kit.library.LibraryItem;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.embed.swing.JFXPanel;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.control.SplitPane;

import javax.swing.*;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class JavaFXPlatformHelper {
  public static void disableImplicitExit() {
    Platform.setImplicitExit(false);
  }

  public static void javafxInvokeLater(Runnable runnable) {
    Platform.runLater(runnable);
  }

  public static void setDefaultClassLoader(ClassLoader loader) {
    FXMLLoader.setDefaultClassLoader(loader);
  }

  public static JComponent createJFXPanel() {
    return new JFXPanel();
  }

  public static void setupJFXPanel(JComponent panel, EditorController editorController) {
    HierarchyTreeViewController componentTree = new HierarchyTreeViewController(editorController);
    ContentPanelController canvas = new ContentPanelController(editorController);
    InspectorPanelController propertyTable = new InspectorPanelController(editorController);
    LibraryPanelController palette = new LibraryPanelController(editorController);

    SplitPane leftPane = new SplitPane();
    leftPane.setOrientation(Orientation.VERTICAL);
    leftPane.getItems().addAll(palette.getPanelRoot(), componentTree.getPanelRoot());
    leftPane.setDividerPositions(0.5, 0.5);

    SplitPane.setResizableWithParent(leftPane, Boolean.FALSE);
    SplitPane.setResizableWithParent(propertyTable.getPanelRoot(), Boolean.FALSE);

    SplitPane mainPane = new SplitPane();

    mainPane.getItems().addAll(leftPane, canvas.getPanelRoot(), propertyTable.getPanelRoot());
    mainPane.setDividerPositions(0.11036789297658862, 0.8963210702341137);

    ((JFXPanel)panel).setScene(new Scene(mainPane, panel.getWidth(), panel.getHeight(), true, SceneAntialiasing.BALANCED));
  }

  public static Object createChangeListener(Runnable command) {
    return (ChangeListener<Number>)(observable, oldValue, newValue) -> command.run();
  }

  @SuppressWarnings("unchecked")
  public static void addListeners(EditorController controller, Object listener, Object selectionListener) {
    controller.getJobManager().revisionProperty().addListener((ChangeListener<Number>)listener);
    controller.getSelection().revisionProperty().addListener((ChangeListener<Number>)selectionListener);
  }

  @SuppressWarnings("unchecked")
  public static void removeListeners(EditorController editorController, Object listener, Object selectionListener) {
    if (editorController != null) {
      if (listener != null) {
        editorController.getJobManager().revisionProperty().removeListener((ChangeListener<Number>)listener);
      }
      if (selectionListener != null) {
        editorController.getSelection().revisionProperty().removeListener((ChangeListener<Number>)selectionListener);
      }
    }
  }

  public static class CustomComponent {
    private final String myName;
    private final String myQualifiedName;
    private final String myModule;
    private final Map<String, String> myAttributes;

    public CustomComponent(String name,
                           String qualifiedName,
                           String module,
                           Map<String, String> attributes) {
      myName = name;
      myQualifiedName = qualifiedName;
      myModule = module;
      myAttributes = attributes;
    }

    public String getDisplayName() {
      return myModule != null && !myModule.isEmpty() ? myName + " (" + myModule + ")" : myName;
    }

    public String getFxmlText() {
      final StringBuilder builder =
              new StringBuilder(String.format("<?xml version=\"1.0\" encoding=\"UTF-8\"?><?import %s?><%s", myQualifiedName, myName));
      myAttributes.forEach((name, value) -> builder.append(String.format(" %s=\"%s\"", name, value.replace("\"", "&quot;"))));
      builder.append("/>");
      return builder.toString();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof CustomComponent)) return false;

      CustomComponent c = (CustomComponent)o;
      return myQualifiedName.equals(c.myQualifiedName) && Objects.equals(myModule, c.myModule);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myQualifiedName, myModule);
    }

    @Override
    public String toString() {
      return myModule != null ? myQualifiedName + "(" + myModule + ")" : myQualifiedName;
    }
  }

  public static class CustomLibrary extends Library {
    private static final String CUSTOM_SECTION = "Custom";

    public CustomLibrary(ClassLoader classLoader, List<CustomComponent> customComponents) {
      classLoaderProperty.set(classLoader);

      getItems().setAll(BuiltinLibrary.getLibrary().getItems());
      final List<LibraryItem> items = customComponents.stream().map(component -> new LibraryItem(component.getDisplayName(), CUSTOM_SECTION, component.getFxmlText(), null, this)).collect(Collectors.toList());
      getItems().addAll(items);
    }

    @Override
    public Comparator<String> getSectionComparator() {
      return CustomLibrary::compareSections;
    }

    private static int compareSections(String s1, String s2) {
      final boolean isCustom1 = CUSTOM_SECTION.equals(s1);
      final boolean isCustom2 = CUSTOM_SECTION.equals(s2);
      if (isCustom1) return isCustom2 ? 0 : 1;
      if (isCustom2) return -1;
      return BuiltinLibrary.getLibrary().getSectionComparator().compare(s1, s2);
    }
  }
}
