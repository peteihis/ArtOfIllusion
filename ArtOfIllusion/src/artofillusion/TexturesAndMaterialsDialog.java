/* Copyright (C) 2011 by Helge Hansen and Peter Eastman

   This program is free software; you can redistribute it and/or modify it under the
   terms of the GNU General Public License as published by the Free Software
   Foundation; either version 2 of the License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful, but WITHOUT ANY
   WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
   PARTICULAR PURPOSE.  See the GNU General Public License for more details. */

package artofillusion;

import artofillusion.image.*;
import artofillusion.material.*;
import artofillusion.texture.*;
import artofillusion.ui.*;
import buoy.event.*;
import buoy.widget.*;

import javax.swing.event.*;
import javax.swing.tree.*;
import java.awt.*;
import java.io.*;
import java.lang.ref.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.List;

public class TexturesAndMaterialsDialog extends BDialog
{
  Scene theScene;
  EditingWindow parentFrame;
  BTree libraryList;
  Scene libraryScene;
  File libraryFile;
  Scene selectedScene;
  Texture selectedTexture;
  Material selectedMaterial;
  BButton duplicateButton, deleteButton, editButton;
  BButton loadLibButton, saveLibButton, deleteLibButton, newFileButton, includeFileButton, closeButton;
  BComboBox typeChoice;
  List<Texture> textureTypes = PluginRegistry.getPlugins(Texture.class); // List<Texture>
  List<Material> materialTypes = PluginRegistry.getPlugins(Material.class); // List<Material>
  MaterialPreviewer matPre;
  BLabel status;
  String[] statusLine = new String[3];
  BLabel matInfo;
  String[] matInfoLine = new String[5];
  static String CURRENT_SCENE_TEXT = "Current Scene";
  Scene externalScene;
  File mainFolder;
  private boolean showTextures, showMaterials;
  private final ArrayList<Object> rootNodes;

  static String TEXTURE_SYMBOL = "\u25E6 "; // white bullet
  static String MATERIAL_SYMBOL = "\u2022 "; // bullet
  static String EXTERNAL_FILE_SYMBOL = "# ";
  static String LIBRARY_VERSION = "prototype 0.1";

  ListChangeListener listListener = new ListChangeListener()
  {
    public void itemAdded(int index, java.lang.Object obj)
    {
      ((SceneTreeModel) libraryList.getModel()).rebuildScenes();
    }

    public void itemChanged(int index, java.lang.Object obj)
    {
      ((SceneTreeModel) libraryList.getModel()).rebuildScenes();
    }

    public void itemRemoved(int index, java.lang.Object obj)
    {
      ((SceneTreeModel) libraryList.getModel()).rebuildScenes();
    }
  };

  public TexturesAndMaterialsDialog(EditingWindow frame, Scene aScene)
  {

    super(frame.getFrame(), "Textures and Materials Library", false);

    parentFrame = frame;
    theScene = aScene;

    theScene.addMaterialListener(listListener);
    theScene.addTextureListener(listListener);

    mainFolder = new File(System.getProperties().getProperty("user.dir"), "Textures and Materials");

    BorderContainer content = new BorderContainer();
    setContent(BOutline.createEmptyBorder(content, UIUtilities.getStandardDialogInsets()));

    // list:

    libraryList = new BTree();

    libraryList.setMultipleSelectionEnabled(false);
    libraryList.addEventLink(SelectionChangedEvent.class, this, "doSelectionChanged");
    libraryList.addEventLink(MouseClickedEvent.class, this, "mouseClicked");

    BScrollPane listWrapper = new BScrollPane(libraryList, BScrollPane.SCROLLBAR_AS_NEEDED, BScrollPane.SCROLLBAR_AS_NEEDED);
    listWrapper.setPreferredViewSize(new Dimension(250, 250));

    content.add(listWrapper, BorderContainer.WEST);

    // preview:

    BorderContainer matBox = new BorderContainer();

    Texture tx0 = theScene.getTexture(0); // initial texture
    matPre = new MaterialPreviewer(tx0, null, 300, 300); // size to be determined
    matBox.add(matPre, BorderContainer.CENTER); // preview must be in the center part to be resizeable

    ColumnContainer infoBox = new ColumnContainer();

    matInfo = new BLabel();
    BOutline matBorder = BOutline.createEmptyBorder(matInfo, 3); // a little space around the text
    setMatInfoText(matInfoLine); // init
    infoBox.add(matBorder);
    infoBox.setDefaultLayout(new LayoutInfo(LayoutInfo.WEST, LayoutInfo.BOTH));

    matBox.add(infoBox, BorderContainer.SOUTH);

    content.add(matBox, BorderContainer.CENTER);

    // buttons:
    ColumnContainer buttons = new ColumnContainer();
    buttons.setDefaultLayout(new LayoutInfo(LayoutInfo.CENTER, LayoutInfo.HORIZONTAL, null, null));
    content.add(buttons, BorderContainer.EAST);

    BLabel label1 = new BLabel("Scene Functions:", BLabel.CENTER);
    buttons.add(label1);

    typeChoice = new BComboBox();
    typeChoice.add("New...");

    java.lang.reflect.Method mtd;

    for (int j = 0; j < textureTypes.size(); j++)
    {
      try
      {
        mtd = textureTypes.get(j).getClass().getMethod("getTypeName", null);
        typeChoice.add((String) mtd.invoke(null, null)+" texture");
      }
      catch (Exception ex)
      {
      }
    }
    for (int j = 0; j < materialTypes.size(); j++)
    {
      try
      {
        mtd = materialTypes.get(j).getClass().getMethod("getTypeName", null);
        typeChoice.add((String) mtd.invoke(null, null)+" material");
      }
      catch (Exception ex)
      {
      }
    }
    typeChoice.addEventLink(ValueChangedEvent.class, this, "doNew");

    buttons.add(typeChoice);

    duplicateButton = new BButton("Duplicate...");
    duplicateButton.addEventLink(CommandEvent.class, this, "doCopy");
    buttons.add(duplicateButton);

    deleteButton = new BButton("Delete...");
    deleteButton.addEventLink(CommandEvent.class, this, "doDelete");
    buttons.add(deleteButton);

    editButton = new BButton("Edit...");
    editButton.addEventLink(CommandEvent.class, this, "doEdit");
    buttons.add(editButton);

    BSeparator space1 = new BSeparator();
    buttons.add(space1);

    BLabel label2 = new BLabel("Library Functions:", BLabel.CENTER);
    buttons.add(label2);

    loadLibButton = new BButton("Load from Library");
    loadLibButton.addEventLink(CommandEvent.class, this, "doLoadLib");
    buttons.add(loadLibButton);

    saveLibButton = new BButton("Save to Library...");
    saveLibButton.addEventLink(CommandEvent.class, this, "doSaveLib");
    buttons.add(saveLibButton);

    deleteLibButton = new BButton("Delete from Library...");
    deleteLibButton.addEventLink(CommandEvent.class, this, "doDeleteLib");
    buttons.add(deleteLibButton);

    newFileButton = new BButton("New File...");
    newFileButton.addEventLink(CommandEvent.class, this, "doNewLib");
    buttons.add(newFileButton);

    includeFileButton = new BButton("Include File...");
    includeFileButton.addEventLink(CommandEvent.class, this, "doIncludeLib");
    buttons.add(includeFileButton);

    BSeparator space2 = new BSeparator();
    buttons.add(space2);

    closeButton = new BButton("Close");
    closeButton.addEventLink(CommandEvent.class, this, "dispose");
    buttons.add(closeButton);

    status = new BLabel();
    setStatusText(statusLine); // init
    setStatusText(3, "Version: "+LIBRARY_VERSION);
    content.add(status, BorderContainer.SOUTH);

    hilightButtons();

    addEventLink(WindowClosingEvent.class, this, "dispose");
    rootNodes = new ArrayList<Object>();
    showTextures = true;
    showMaterials = true;
    buildList();
    rootNodes.add(new SceneTreeNode(null, theScene));
    for (File file : mainFolder.listFiles())
    {
      if (file.isDirectory())
        rootNodes.add(new FolderTreeNode(file));
      else if (file.getName().endsWith(".aoi"))
        rootNodes.add(new SceneTreeNode(file));
    }
    libraryList.setModel(new SceneTreeModel());
    setSelection(libraryList.getRootNode(), theScene, theScene.getDefaultTexture());
    pack();
    UIUtilities.centerDialog(this, parentFrame.getFrame());
    setVisible(true);
  }

  private String getTypeName(Object item)
  {
    String typeName = "";
    try
    {
      Method mtd = item.getClass().getMethod("getTypeName", null);
      typeName = (String) mtd.invoke(null, null);
    }
    catch (Exception ex)
    {
    }
    return typeName;
  }

  public void doSelectionChanged()
  {
    // find selection:
    // we need to find the index of the texture or material, since there may be more than one with the same name
    // however, there will never be two files or folders on the same level with the same name

    TreePath selection = libraryList.getSelectedNode();
    selectedTexture = null;
    selectedMaterial = null;
    if (selection != null && libraryList.isLeafNode(selection))
    {
      TreePath parentNode = libraryList.getParentNode(selection);
      SceneTreeNode sceneNode = (SceneTreeNode) parentNode.getLastPathComponent();
      try
      {
        selectedScene = sceneNode.getScene();
        Object node = selection.getLastPathComponent();
        if (node instanceof TextureTreeNode)
        {
          selectedTexture = selectedScene.getTexture(((TextureTreeNode) node).index);
          matPre.setTexture(selectedTexture, selectedTexture.getDefaultMapping(matPre.getObject().getObject()));
          matPre.setMaterial(null, null);
          matPre.render();
          setMatInfoText(1, "Texture Name: "+selectedTexture.getName());
          setMatInfoText(2, "Texture Type: "+getTypeName(selectedTexture));
        }
        else
        {
          selectedMaterial = selectedScene.getMaterial(((MaterialTreeNode) node).index);
          Texture tex = UniformTexture.invisibleTexture();
          matPre.setTexture(tex, tex.getDefaultMapping(matPre.getObject().getObject()));
          matPre.setMaterial(selectedMaterial, selectedMaterial.getDefaultMapping(matPre.getObject().getObject()));
          matPre.render();
          setMatInfoText(1, "Material Name: "+selectedMaterial.getName());
          setMatInfoText(2, "Material Type: "+getTypeName(selectedMaterial));
        }
      }
      catch (IOException ex)
      {
        new BStandardDialog("", Translate.text("errorLoadingFile")+": "+ex.getLocalizedMessage(), BStandardDialog.ERROR).showMessageDialog(this);
      }
    }
    if (selectedTexture == null && selectedMaterial == null)
    {
      Texture tex = UniformTexture.invisibleTexture();
      matPre.setTexture(tex, tex.getDefaultMapping(matPre.getObject().getObject()));
      matPre.setMaterial(null, null);
      matPre.render();
      setMatInfoText(1, "(No selection)");
      setMatInfoText(2, "");

      // toggle: expand all child nodes?
    }

    hilightButtons();
  }

  private boolean setSelection(TreePath node, Scene scene, Object object)
  {
    Object value = node.getLastPathComponent();
    if (value instanceof FolderTreeNode && !libraryList.isNodeExpanded(node))
      return false;
    TreeModel model = libraryList.getModel();
    int numChildren = model.getChildCount(value);
    if (value instanceof SceneTreeNode)
    {
      SceneTreeNode stn = (SceneTreeNode) value;
      if (stn.scene == null || stn.scene.get() != scene)
        return false;
      for (int i = 0; i < numChildren; i++)
      {
        Object child = model.getChild(value, i);
        if ((child instanceof TextureTreeNode && scene.getTexture(((TextureTreeNode) child).index) == object) ||
          (child instanceof MaterialTreeNode && scene.getMaterial(((MaterialTreeNode) child).index) == object))
        {
          libraryList.setNodeSelected(node.pathByAddingChild(child), true);
          doSelectionChanged();
          return true;
        }
      }
      return false;
    }
    for (int i = 0; i < numChildren; i++)
    {
      if (setSelection(node.pathByAddingChild(model.getChild(value, i)), scene, object))
        return true;
    }
    return false;
  }

  public void hilightButtons()
  {
    if (selectedTexture == null && selectedMaterial == null)
    {
      duplicateButton.setEnabled(false);
      deleteButton.setEnabled(false);
      editButton.setEnabled(false);
      loadLibButton.setEnabled(false);
      saveLibButton.setEnabled(false);
      deleteLibButton.setEnabled(false);
      //newFileButton.setEnabled(true);
      //includeFileButton.setEnabled(true);
      //closeButton.setEnabled(true);
    }
    else
    {
      hiLight(selectedScene == theScene);
    }
  }

  private void hiLight(boolean h)
  {
    duplicateButton.setEnabled(h);
    deleteButton.setEnabled(h);
    editButton.setEnabled(h);
    loadLibButton.setEnabled(!h);
    saveLibButton.setEnabled(h);
    deleteLibButton.setEnabled(!h);
  }

  public void mouseClicked(MouseClickedEvent ev)
  {
    if (ev.getClickCount() == 2)
    {
      doEdit();
    }
    else if (ev.getClickCount() == 1)
    {
      doSelectionChanged();
    }
  }

  // -- keyboard handler:

  // to be implemented:
  // next - previous (arrow down/up)

  // -- button handlers:

  public void doNew()
  {
    int newType = typeChoice.getSelectedIndex()-1;
    if (newType >= 0)
    {
      if (newType >= textureTypes.size())
      {
        // material
        int j = 0;
        String name = "";
        do
        {
          j++;
          name = "Untitled "+j;
        } while (theScene.getMaterial(name) != null);
        try
        {
          Material mat = materialTypes.get(newType-textureTypes.size()).getClass().newInstance();
          mat.setName(name);
          mat.edit(parentFrame.getFrame(), theScene);
          theScene.addMaterial(mat); // need to add it after edit for the name to be correct in the list
        }
        catch (Exception ex)
        {
        }
        parentFrame.setModified();
        selectLastCurrentMaterial(); // does not work on procedurals(?)
      }
      else
      {
        // texture
        int j = 0;
        String name = "";
        do
        {
          j++;
          name = "Untitled "+j;
        } while (theScene.getTexture(name) != null);
        try
        {
          Texture tex = textureTypes.get(newType).getClass().newInstance();
          tex.setName(name);
          tex.edit(parentFrame.getFrame(), theScene);
          theScene.addTexture(tex); // need to add it after edit for the name to be correct in the list
        }
        catch (Exception ex)
        {
        }
        parentFrame.setModified();
        selectLastCurrentTexture(); // does not work on procedurals(?)
      }
      typeChoice.setSelectedIndex(0);
    }
  }

  public void doCopy()
  { // the Duplicate button is only enabled when an item in the current scene is selected
    if (selectedTexture != null)
    {
      String name = new BStandardDialog("", Translate.text("newTexName"), BStandardDialog.PLAIN).showInputDialog(this, null, "");
      if (name == null)
        return;
      Texture tex = selectedTexture.duplicate();
      tex.setName(name);
      theScene.addTexture(tex);
      parentFrame.setModified();
      selectLastCurrentTexture();
    }
    else if (selectedMaterial != null)
    {
      String name = new BStandardDialog("", Translate.text("newMatName"), BStandardDialog.PLAIN).showInputDialog(this, null, "");
      if (name == null)
        return;
      Material mat = selectedMaterial.duplicate();
      mat.setName(name);
      theScene.addMaterial(mat);
      parentFrame.setModified();
      selectLastCurrentMaterial();
    }
  }

  public void doDelete()
  {
    if (selectedTexture != null)
    {
      String[] options = new String[]{Translate.text("button.ok"), Translate.text("button.cancel")};
      int choice = new BStandardDialog("", Translate.text("deleteTexture", selectedTexture.getName()), BStandardDialog.PLAIN).showOptionDialog(this, options, options[1]);
      if (choice == 0)
      {
        theScene.removeTexture(theScene.indexOf(selectedTexture));
        parentFrame.setModified();
        setSelection(libraryList.getRootNode(), theScene, theScene.getDefaultTexture());
      }
    }
    else if (selectedMaterial != null)
    {
      String[] options = new String[]{Translate.text("button.ok"), Translate.text("button.cancel")};
      int choice = new BStandardDialog("", Translate.text("deleteMaterial", selectedMaterial.getName()), BStandardDialog.PLAIN).showOptionDialog(this, options, options[1]);
      if (choice == 0)
      {
        theScene.removeMaterial(theScene.indexOf(selectedMaterial));
        parentFrame.setModified();
        setSelection(libraryList.getRootNode(), theScene, theScene.getDefaultTexture());
      }
    }
  }

  public void doEdit()
  {
    if (selectedScene != theScene)
      return;
    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    if (selectedTexture != null)
    {
      Texture tex = selectedTexture;
      tex.edit(parentFrame.getFrame(), theScene);
      tex.assignNewID();
      theScene.changeTexture(theScene.indexOf(tex));
      parentFrame.setModified();
    }
    else if (selectedMaterial != null)
    {
      Material mat = selectedMaterial;
      mat.edit(parentFrame.getFrame(), theScene);
      mat.assignNewID();
      theScene.changeMaterial(theScene.indexOf(mat));
      parentFrame.setModified();
    }
    setCursor(Cursor.getDefaultCursor());
  }

  // --

  public void doLoadLib()
  {
    if (selectedTexture != null)
    {
      theScene.addTexture(selectedTexture.duplicate());
      parentFrame.setModified();
      for (int i = 0; i < libraryScene.getNumImages(); i++)
      {
        ImageMap image = libraryScene.getImage(i);
        if (selectedTexture.usesImage(image))
        {
          theScene.addImage(image);
        }
      }
      parentFrame.updateImage();
//      updateCurrentScene();
      selectLastCurrentTexture();
    }
    else if (selectedMaterial != null)
    {
      theScene.addMaterial(selectedMaterial);
      parentFrame.setModified();
      for (int i = 0; i < libraryScene.getNumImages(); i++)
      {
        ImageMap image = libraryScene.getImage(i);
        if (selectedMaterial.usesImage(image))
        {
          theScene.addImage(image);
        }
      }
      parentFrame.updateImage();
//      updateCurrentScene();
      selectLastCurrentMaterial();
    }
    hilightButtons();
  }

  // if the file is locked, AoI will make a tmp-file and try to save there
  public void doSaveLib()
  {
    String itemText;
    if (selectedTexture != null)
    {
      itemText = "texture";
    }
    else
    {
      itemText = "material";
    }
    if (selectedTexture != null || selectedMaterial != null)
    {
      BFileChooser fcOut = new BFileChooser(BFileChooser.OPEN_FILE, "Choose an Art of Illusion Scene File (.aoi) to save the "+itemText+" to", mainFolder);
      // ff = new FileNameExtensionFilter ("Art of Illusion Scene File", "aoi"); // halts here
      // fcOut.setFileFilter (ff);
      if (fcOut.showDialog(this))
      {
        File saveFile = fcOut.getSelectedFile();
        if (saveFile.exists())
        {
          // saveFileName = saveFile.getName();
          try
          {
            Scene saveScene = new Scene(saveFile, true);
            if (selectedTexture != null)
            {
              saveScene.addTexture(selectedTexture);
              saveScene.writeToFile(saveFile); // ... but all the objects are preserved
            }
            else if (selectedMaterial != null)
            {
              saveScene.addMaterial(selectedMaterial);
              saveScene.writeToFile(saveFile);
            }
          }
          catch (IOException ex)
          {
            ex.printStackTrace();
          }
        }
        buildList(); // should use next statement instead, but ...
        // updateNode (someSelection, saveScene); // ... how to get selection?
        setSelection(libraryList.getRootNode(), theScene, theScene.getDefaultTexture());
      }
    }
  }

  // if the file is locked, AoI will make a tmp-file and try to save there
  public void doDeleteLib()
  {
    try
    {
      if (selectedTexture != null)
      {
        String[] options = new String[]{Translate.text("button.ok"), Translate.text("button.cancel")};
        int choice = new BStandardDialog("", Translate.text("deleteTexture", selectedTexture.getName()), BStandardDialog.PLAIN).showOptionDialog(this, options, options[1]);
        if (choice == 0)
        {
          int texIndex = libraryScene.indexOf(selectedTexture);
          libraryScene.removeTexture(texIndex);
          libraryScene.writeToFile(libraryFile);
          buildList(); // should update only the affected file in the list
          setSelection(libraryList.getRootNode(), theScene, theScene.getDefaultTexture());
        }
      }
      else if (selectedMaterial != null)
      {
        String[] options = new String[]{Translate.text("button.ok"), Translate.text("button.cancel")};
        int choice = new BStandardDialog("", Translate.text("deleteMaterial", selectedMaterial.getName()), BStandardDialog.PLAIN).showOptionDialog(this, options, options[1]);
        if (choice == 0)
        {
          int matIndex = libraryScene.indexOf(selectedMaterial);
          libraryScene.removeMaterial(matIndex);
          libraryScene.writeToFile(libraryFile);
          buildList(); // should update only the affected file in the list
          setSelection(libraryList.getRootNode(), theScene, theScene.getDefaultTexture());
        }
      }
    }
    catch (IOException ex)
    {
      ex.printStackTrace();
    }
  }

  public void doNewLib()
  {
    BFileChooser fcNew = new BFileChooser(BFileChooser.SAVE_FILE, "Create new Art of Illusion Scene File (.aoi) in the library", mainFolder);
    if (fcNew.showDialog(this))
    {
      File saveFile = fcNew.getSelectedFile();
      if (!saveFile.exists())
      {
        try
        {
          theScene.writeToFile(saveFile);
        }
        catch (IOException ex)
        {
          ex.printStackTrace();
        }
        buildList(); // should build only affected folder
        selectCurrent(0); // should select new file
      }
      else
      {
        BStandardDialog d = new BStandardDialog("Error", "File already exists.", BStandardDialog.ERROR);
        d.showMessageDialog(this);
      }
    }
  }

  public void doIncludeLib()
  {
    BFileChooser fcInc = new BFileChooser(BFileChooser.OPEN_FILE, "Choose an Art of Illusion Scene File (.aoi)");
    if (fcInc.showDialog(this))
    {
      File inputFile = fcInc.getSelectedFile();
      if (inputFile.exists())
      {
        addExternalFile(inputFile);
        selectCurrent(0); // not quite right
      }
    }
  }

  // --

  public void dispose()
  {
    theScene.removeMaterialListener(listListener);
    theScene.removeTextureListener(listListener);
    super.dispose();
  }

  // -- textmessage fields:

  // three lines of text:
  // 1: (not used - could display tool tips)
  // 2: time-consuming task info (building list)
  // 3: version info
  public void setStatusText(String[] lines)
  {
    String s = "<html>";
    for (int i = 0; i < lines.length; i++)
    {
      if (lines[i] == null || lines[i].equals("")) statusLine[i] = "&nbsp;";
      else statusLine[i] = lines[i];
      s = s+"<p>"+statusLine[i]+"</p>";
    }
    s = s+"</html>";
    status.setText(s);
  }

  public void setStatusText(int nr, String text)
  {
    statusLine[nr-1] = text;
    setStatusText(statusLine);
  }

  // five lines of text:
  // 1: item name & texture/material
  // 2: item type
  // 3-5: (for future use)
  public void setMatInfoText(String[] lines)
  {
    String s = "<html>";
    for (int i = 0; i < lines.length; i++)
    {
      if (lines[i] == null || lines[i].equals("")) matInfoLine[i] = "&nbsp;";
      else matInfoLine[i] = lines[i];
      s = s+"<p>"+matInfoLine[i]+"</p>";
    }
    s = s+"</html>";
    matInfo.setText(s);
  }

  public void setMatInfoText(int nr, String text)
  {
    matInfoLine[nr-1] = text;
    setMatInfoText(matInfoLine);
  }

  // -- list management:

  private void addSceneNodes(TreePath parent, Scene scene)
  {
    if (scene.getNumTextures() > 0)
    {
      for (int i = 0; i < scene.getNumTextures(); i++)
      {
        String textureName = TEXTURE_SYMBOL+scene.getTexture(i).getName();
        DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(textureName, false);
        libraryList.addNode(parent, newNode);
      }
    }
    if (scene.getNumMaterials() > 0)
    {
      for (int i = 0; i < scene.getNumMaterials(); i++)
      {
        String materialName = MATERIAL_SYMBOL+scene.getMaterial(i).getName();
        DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(materialName, false);
        libraryList.addNode(parent, newNode);
      }
    }
  }

  private void deleteNodes(TreePath parent)
  {
    while (libraryList.getChildNodeCount(parent) > 0)
    {
      libraryList.removeNode(libraryList.getChildNode(parent, 0));
    }
  }

  private void addFilesAndFolders(TreePath parent, Scene scene, File filesAndFolders[])
  {
    for (int i = 0; i < filesAndFolders.length; i++)
    {
      if (filesAndFolders[i].isFile())
      {
        if (filesAndFolders[i].getName().endsWith(".aoi"))
        { // only .aoi-files
          DefaultMutableTreeNode n0 = new DefaultMutableTreeNode(filesAndFolders[i].getName());
          TreePath r0 = libraryList.addNode(parent, n0);
          try
          {
            addSceneNodes(r0, new Scene(filesAndFolders[i], false));
          }
          catch (IOException ex)
          {
            ex.printStackTrace();
          }
        }
      }
      else if (filesAndFolders[i].isDirectory() && filesAndFolders[i].list().length > 0)
      { // only folders with content (not alias/symlink files)
        DefaultMutableTreeNode folderNodeText = new DefaultMutableTreeNode(filesAndFolders[i].getName());
        TreePath folderNode = libraryList.addNode(parent, folderNodeText);
        addFilesAndFolders(folderNode, scene, filesAndFolders[i].listFiles()); // recursion!
      }
    }
  }

  private void addLibraryNodes(TreePath parent, Scene scene)
  {
    File filesAndFolders[] = mainFolder.listFiles();
    addFilesAndFolders(parent, scene, filesAndFolders);
  }

  private void selectCurrent(int sel)
  {
    TreePath r = libraryList.getRootNode();
    TreePath current = libraryList.getChildNode(r, 0);
    libraryList.setNodeExpanded(current, true);
    libraryList.setNodeSelected(libraryList.getChildNode(current, sel), true);
    doSelectionChanged();
  }

  private void selectLastCurrentTexture()
  {
    TreePath r = libraryList.getRootNode();
    TreePath current = libraryList.getChildNode(r, 0);
    libraryList.setNodeExpanded(current, true);
    int lastIndex = libraryList.getChildNodeCount(current)-theScene.getNumMaterials()-1;
    libraryList.setNodeSelected(libraryList.getChildNode(current, lastIndex), true);
    doSelectionChanged();
  }

  private void selectLastCurrentMaterial()
  {
    TreePath r = libraryList.getRootNode();
    TreePath current = libraryList.getChildNode(r, 0);
    libraryList.setNodeExpanded(current, true);
    int lastIndex = libraryList.getChildNodeCount(current)-1;
    libraryList.setNodeSelected(libraryList.getChildNode(current, lastIndex), true);
    doSelectionChanged();
  }

  private void addExternalFile(File f)
  {
    if (f.getName().endsWith(".aoi"))
    { // only .aoi-files
      TreePath r = libraryList.getRootNode();
      // remove old external if it exists:
      for (int n = libraryList.getChildNodeCount(r)-1; n >= 0; n--)
      { // quicker to start at the end of the list
        TreePath oldNode = libraryList.getChildNode(r, n);
        Object nodePath[] = oldNode.getPath();
        if (nodePath[1].toString().startsWith(EXTERNAL_FILE_SYMBOL))
        {
          libraryList.removeNode(oldNode);
          break; // we have only one at the moment
        }
      }
      DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(EXTERNAL_FILE_SYMBOL+f.getName());
      try
      {
        externalScene = new Scene(f, false);
      }
      catch (IOException ex)
      {
        ex.printStackTrace();
      }
      addSceneNodes(libraryList.addNode(r, newNode), externalScene);
    }
  }

  // build list at startup and when a file/folder is changed:
  public void buildList()
  {
    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    setStatusText(2, "Rebuilding library list ..."); // does not appear?
    TreePath r = libraryList.getRootNode();

    deleteNodes(r);

    libraryList.setRootNodeShown(false);
    libraryList.setNodeExpanded(r, true);

    DefaultMutableTreeNode n0 = new DefaultMutableTreeNode(CURRENT_SCENE_TEXT);
    TreePath r0 = libraryList.addNode(r, n0);
    addSceneNodes(r0, theScene);

    addLibraryNodes(r, theScene);

    setStatusText(2, "Finished building library list.");
    setCursor(Cursor.getDefaultCursor());
  }

  private class TextureTreeNode
  {
    int index;
    String name;
    SceneTreeNode scene;

    TextureTreeNode(SceneTreeNode scene, int index) throws IOException
    {
      this.scene = scene;
      this.index = index;
      name = scene.getScene().getTexture(index).getName();
    }

    @Override
    public String toString()
    {
      return name;
    }
  }

  private class MaterialTreeNode
  {
    int index;
    String name;
    SceneTreeNode scene;

    MaterialTreeNode(SceneTreeNode scene, int index) throws IOException
    {
      this.scene = scene;
      this.index = index;
      name = scene.getScene().getMaterial(index).getName();
    }

    @Override
    public String toString()
    {
      return name;
    }
  }

  private class SceneTreeNode
  {
    ArrayList<TextureTreeNode> textures;
    ArrayList<MaterialTreeNode> materials;
    SoftReference<Scene> scene;
    final File file;

    SceneTreeNode(File file)
    {
      this.file = file;
    }

    SceneTreeNode(File file, Scene scene)
    {
      this.file = file;
      this.scene = new SoftReference<Scene>(scene);
    }

    void ensureChildrenValid()
    {
      if (textures == null)
      {
        try
        {
          Scene theScene = getScene();
          textures = new ArrayList<TextureTreeNode>();
          for (int i = 0; i < theScene.getNumTextures(); i++)
            textures.add(new TextureTreeNode(this, i));
          materials = new ArrayList<MaterialTreeNode>();
          for (int i = 0; i < theScene.getNumMaterials(); i++)
            materials.add(new MaterialTreeNode(this, i));
        }
        catch (IOException ex)
        {
          ex.printStackTrace();
        }
      }
    }

    Scene getScene() throws IOException
    {
      if (scene != null)
      {
        Scene theScene = scene.get();
        if (theScene != null)
          return theScene;
      }
      Scene theScene = new Scene(file, true);
      scene = new SoftReference<Scene>(theScene);
      return theScene;
    }

    @Override
    public String toString()
    {
      if (file == null)
        return Translate.text("currentScene");
      return file.getName().substring(0, file.getName().length()-4);
    }
  }

  private class FolderTreeNode
  {
    final File file;
    ArrayList<Object> children;

    FolderTreeNode(File file)
    {
      this.file = file;
    }

    ArrayList<Object> getChildren()
    {
      if (children == null)
      {
        children = new ArrayList<Object>();
        for (File f : file.listFiles())
        {
          if (f.isDirectory())
            children.add(new FolderTreeNode(f));
          else if (f.getName().endsWith(".aoi"))
            children.add(new SceneTreeNode(f));
        }
      }
      return children;
    }

    @Override
    public String toString()
    {
      return file.getName();
    }
  }

  private class SceneTreeModel implements TreeModel
  {
    private ArrayList<TreeModelListener> listeners = new ArrayList<TreeModelListener>();
    private DefaultMutableTreeNode root = new DefaultMutableTreeNode();

    public void addTreeModelListener(TreeModelListener listener)
    {
      listeners.add(listener);
    }

    public void removeTreeModelListener(TreeModelListener listener)
    {
      listeners.remove(listener);
    }

    public Object getRoot()
    {
      return root;
    }

    public Object getChild(Object o, int i)
    {
      if (o == root)
        return rootNodes.get(i);
      if (o instanceof FolderTreeNode)
        return ((FolderTreeNode) o).getChildren().get(i);
      SceneTreeNode node = (SceneTreeNode) o;
      node.ensureChildrenValid();
      if (showTextures)
      {
        if (i < node.textures.size())
          return node.textures.get(i);
        i -= node.textures.size();
      }
      return node.materials.get(i);
    }

    public int getChildCount(Object o)
    {
      if (o == root)
        return rootNodes.size();
      if (o instanceof FolderTreeNode)
        return ((FolderTreeNode) o).getChildren().size();
      if (!(o instanceof SceneTreeNode))
        return 0;
      SceneTreeNode node = (SceneTreeNode) o;
      node.ensureChildrenValid();
      int count = 0;
      if (showTextures)
        count += node.textures.size();
      if (showMaterials)
        count += node.materials.size();
      return count;
    }

    public boolean isLeaf(Object o)
    {
      return !(o == root || o instanceof FolderTreeNode || o instanceof SceneTreeNode);
    }

    public void valueForPathChanged(TreePath treePath, Object o)
    {
    }

    public int getIndexOfChild(Object o, Object o1)
    {
      if (o == root)
        return rootNodes.indexOf((SceneTreeNode) o1);
      if (o instanceof FolderTreeNode)
        return ((FolderTreeNode) o).getChildren().indexOf(o1);
      SceneTreeNode node = (SceneTreeNode) o;
      node.ensureChildrenValid();
      int texIndex = node.textures.indexOf(o1);
      if (texIndex > -1)
        return texIndex;
      int matIndex = node.materials.indexOf(o1);
      if (matIndex > -1)
        return matIndex+(showTextures ? node.textures.size() : 0);;
      return -1;
    }

    void rebuildNode(Object node)
    {
      if (node instanceof SceneTreeNode)
      {
        ((SceneTreeNode) node).textures = null;
        ((SceneTreeNode) node).materials = null;
        return;
      }
      if (node instanceof FolderTreeNode && ((FolderTreeNode) node).children == null)
        return;
      int numChildren = getChildCount(node);
      for (int i = 0; i < numChildren; i++)
        rebuildNode(getChild(node, i));
    }

    void rebuildScenes()
    {
      List<TreePath> expanded = Collections.list(libraryList.getComponent().getExpandedDescendants(libraryList.getRootNode()));
      rebuildNode(root);
      Object selection = (selectedTexture == null ? selectedMaterial : selectedTexture);
      TreeModelEvent ev = new TreeModelEvent(this, new TreePath(root));
      for (TreeModelListener listener : listeners)
        listener.treeStructureChanged(ev);
      for (TreePath path : expanded)
        libraryList.setNodeExpanded(path, true);
      if (selection != null)
        setSelection(new TreePath(root), selectedScene, selection);
    }
  }
}