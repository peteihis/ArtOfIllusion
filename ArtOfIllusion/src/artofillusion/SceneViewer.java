/* Copyright (C) 1999-2006 by Peter Eastman

   This program is free software; you can redistribute it and/or modify it under the
   terms of the GNU General Public License as published by the Free Software
   Foundation; either version 2 of the License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful, but WITHOUT ANY 
   WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
   PARTICULAR PURPOSE.  See the GNU General Public License for more details. */

package artofillusion;

import artofillusion.math.*;
import artofillusion.object.*;
import artofillusion.ui.*;
import artofillusion.view.*;
import buoy.event.*;
import buoy.widget.*;
import java.awt.*;
import java.util.*;

/** The SceneViewer class is a component which displays a view of a Scene. */

public class SceneViewer extends ViewerCanvas
{
  Scene theScene;
  EditingWindow parentFrame;
  Vector cameras;
  boolean draggingBox, draggingSelectionBox, squareBox, sentClick, dragging;
  Point clickPoint, dragPoint;
  ObjectInfo clickedObject;
  int deselect;
  
  public SceneViewer(Scene s, RowContainer p, EditingWindow fr)
  {
    this(s, p, fr, false);
  }

  public SceneViewer(Scene s, RowContainer p, EditingWindow fr, boolean forceSoftwareRendering)
  {
    super(ModellingApp.getPreferences().getUseOpenGL() && isOpenGLAvailable() && !forceSoftwareRendering);
    theScene = s;
    parentFrame = fr;
    addEventLink(MouseClickedEvent.class, this, "mouseClicked");
    draggingBox = draggingSelectionBox = false;
    cameras = new Vector();
    buildChoices(p);
    rebuildCameraList();
    setRenderMode(ModellingApp.getPreferences().getDefaultDisplayMode());
  }

  /** Add all SceneCameras in the scene to list of available views. */
  
  public void rebuildCameraList()
  {
    int i = viewChoice.getItemCount()-2, selected = viewChoice.getSelectedIndex();
    
    while (i > 5)
      viewChoice.remove(i--);
    cameras.removeAllElements();
    for (i = 0; i < theScene.getNumObjects(); i++)
    {
      ObjectInfo obj = theScene.getObject(i);
      if (obj.object instanceof SceneCamera)
      {
        cameras.addElement(obj);
        viewChoice.add(viewChoice.getItemCount()-1, obj.name);
        if (obj == boundCamera)
          selected = viewChoice.getItemCount()-2;
      }
    }
    if (selected < viewChoice.getItemCount())
      viewChoice.setSelectedIndex(selected);
    else
      viewChoice.setSelectedIndex(viewChoice.getItemCount()-1);
    if (viewChoice.getParent() != null)
      viewChoice.getParent().layoutChildren();
  }

  /** Deal with selecting a SceneCamera from the choice menu. */
  
  public void selectOrientation(int which)
  {
    super.selectOrientation(which);
    if (which > 5 && which < viewChoice.getItemCount()-1)
    {
      boundCamera = (ObjectInfo) cameras.elementAt(which-6);
      CoordinateSystem coords = theCamera.getCameraCoordinates();
      coords.copyCoords(boundCamera.coords);
      theCamera.setCameraCoordinates(coords);
    }
    else
      boundCamera = null;
  }
  
  public int getOrientationChoice()
  {
    return viewChoice.getSelectedIndex();
  }
  
  /** Estimate the range of depth values that the camera will need to render.  This need not be exact,
      but should err on the side of returning bounds that are slightly too large.
      @return the two element array {minDepth, maxDepth}
   */
  
  public double[] estimateDepthRange()
  {
    double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
    Mat4 toView = theCamera.getWorldToView();
    for (int i = 0; i < theScene.getNumObjects(); i++)
    {
      ObjectInfo info = theScene.getObject(i);
      BoundingBox bounds = info.getBounds();
      double dx = bounds.maxx-bounds.minx;
      double dy = bounds.maxy-bounds.miny;
      double dz = bounds.maxz-bounds.minz;
      double size = 0.5*Math.sqrt(dx*dx+dy*dy+dz*dz);
      double depth = toView.times(info.coords.getOrigin()).z;
      if (depth-size < min)
        min = depth-size;
      if (depth+size > max)
        max = depth+size;
    }
    return new double [] {min, max};
  }

  public void updateImage()
  {
    ObjectInfo obj;
    Vec3 viewdir;
    int i;

    adjustCamera(perspectiveChoice.getSelectedIndex() == 0);
    super.updateImage();
    
    // Draw the objects.
    
    viewdir = theCamera.getViewToWorld().timesDirection(Vec3.vz());
    for (i = 0; i < theScene.getNumObjects(); i++)
    {
      obj = theScene.getObject(i);
      if (obj == boundCamera)
        continue;
      theCamera.setObjectTransform(obj.coords.fromLocal());
      renderObject(obj, viewdir);
    }

    // Hilight the selection.

    if (currentTool.hilightSelection())
    {
      Rectangle bounds;
      int hsize;
      Color col;
      
      for (i = 0; i < theScene.getNumObjects(); i++)
      {
        obj = theScene.getObject(i);
        if (obj.selected)
        {
          hsize = Scene.HANDLE_SIZE;
          col = handleColor;
        }
        else if (obj.parentSelected)
        {
          hsize = Scene.HANDLE_SIZE/2;
          col = highlightColor;
        }
        else
          continue;
        theCamera.setObjectTransform(obj.coords.fromLocal());
        bounds = theCamera.findScreenBounds(obj.getBounds());
        if (bounds != null)
        {
          drawBox(bounds.x, bounds.y, hsize, hsize, col);
          drawBox(bounds.x+bounds.width-hsize+1, bounds.y, hsize, hsize, col);
          drawBox(bounds.x, bounds.y+bounds.height-hsize+1, hsize, hsize, col);
          drawBox(bounds.x+bounds.width-hsize+1, bounds.y+bounds.height-hsize+1, hsize, hsize, col);
          drawBox(bounds.x+(bounds.width-hsize)/2, bounds.y, hsize, hsize, col);
          drawBox(bounds.x, bounds.y+(bounds.height-hsize)/2, hsize, hsize, col);
          drawBox(bounds.x+(bounds.width-hsize)/2, bounds.y+bounds.height-hsize+1, hsize, hsize, col);
          drawBox(bounds.x+bounds.width-hsize+1, bounds.y+(bounds.height-hsize)/2, hsize, hsize, col);
        }
      }
    }
    
    // Finish up.
    
    drawBorder();
    if (showAxes)
      drawCoordinateAxes();
  }
  
  /** Render a single object into the scene.  viewdir is the
      direction from which the object is being viewed in world coordinates. */
  
  private void renderObject(ObjectInfo obj, Vec3 viewdir)
  {
    RenderingMesh mesh;

    if (!obj.visible)
      return;
    if (theCamera.visibility(obj.getBounds()) == Camera.NOT_VISIBLE)
      return;
    if (obj.object instanceof ObjectCollection)
    {
      Mat4 m = theCamera.getObjectToWorld();
      Enumeration enm = ((ObjectCollection) obj.object).getObjects(obj, true, theScene);
      while (enm.hasMoreElements())
      {
        ObjectInfo info = (ObjectInfo) enm.nextElement();
        CoordinateSystem coords = info.coords.duplicate();
        coords.transformCoordinates(m);
        theCamera.setObjectTransform(coords.fromLocal());
        renderObject(info, info.coords.toLocal().timesDirection(viewdir));
      }
      return;
    }
    if (renderMode == RENDER_WIREFRAME)
    {
      renderWireframe(obj.getWireframePreview(), theCamera, lineColor);
      return;
    }
    mesh = obj.getPreviewMesh();
    if (mesh != null)
    {
      VertexShader shader;
      if (renderMode == RENDER_TRANSPARENT)
      {
        shader = new ConstantVertexShader(transparentColor);
        renderMeshTransparent(mesh, shader, theCamera, theCamera.getViewToWorld().timesDirection(Vec3.vz()), null);
      }
      else
      {
        if (renderMode == RENDER_FLAT)
          shader = new FlatVertexShader(mesh, obj.object, theScene.getTime(), obj.coords.toLocal().timesDirection(viewdir));
        else if (renderMode == RENDER_SMOOTH)
          shader = new SmoothVertexShader(mesh, obj.object, theScene.getTime(), obj.coords.toLocal().timesDirection(viewdir));
        else
          shader = new TexturedVertexShader(mesh, obj.object, theScene.getTime(), obj.coords.toLocal().timesDirection(viewdir)).optimize();
        renderMesh(mesh, shader, theCamera, obj.object.isClosed(), null);
      }
    }
    else
      renderWireframe(obj.getWireframePreview(), theCamera, lineColor);
  }

  /** Begin dragging a box.  The variable square determines whether the box should be
      constrained to be square. */
  
  public void beginDraggingBox(Point p, boolean square)
  {
    draggingBox = true;
    clickPoint = p;
    squareBox = square;
    dragPoint = null;
  }

  /** When the user presses the mouse, forward events to the current tool as appropriate.
      If this is an object based tool, allow them to select or deselect objects. */

  protected void mousePressed(WidgetMouseEvent e)
  {
    int i, j, k, sel[], minarea;
    Rectangle bounds = null;
    ObjectInfo info;
    Point p;

    requestFocus();
    sentClick = true;
    deselect = -1;
    dragging = true;
    clickPoint = e.getPoint();
    clickedObject = null;
    
    // Determine which tool is active.
    
    if (metaTool != null && e.isMetaDown())
      activeTool = metaTool;
    else if (altTool != null && e.isAltDown())
      activeTool = altTool;
    else
      activeTool = currentTool;

    // If the current tool wants all clicks, just forward the event and return.

    if ((activeTool.whichClicks() & EditingTool.ALL_CLICKS) != 0)
    {
      moveToGrid(e);
      activeTool.mousePressed(e, this);
      return;
    }
    
    // See whether the click was on a currently selected object.
    
    p = e.getPoint();
    sel = theScene.getSelection();
    for (i = 0; i < sel.length; i++)
    {
      info = theScene.getObject(sel[i]);
      theCamera.setObjectTransform(info.coords.fromLocal());
      bounds = theCamera.findScreenBounds(info.getBounds());
      if (bounds != null && pointInRectangle(p, bounds))
      {
        clickedObject = info;
        break;
      }
    }
    if (i < sel.length)
    {
      // The click was on a selected object.  If it was a shift-click, the user may want
      // to deselect it, so set a flag.
      
      if (e.isShiftDown())
        deselect = sel[i];
      
      // If the current tool wants handle clicks, then check to see whether the click
      // was on a handle.
      
      if ((activeTool.whichClicks() & EditingTool.HANDLE_CLICKS) != 0)
      {
        if (p.x <= bounds.x+Scene.HANDLE_SIZE)
          j = 0;
        else if (p.x >= bounds.x+(bounds.width-Scene.HANDLE_SIZE)/2 && 
            p.x <= bounds.x+(bounds.width-Scene.HANDLE_SIZE)/2+Scene.HANDLE_SIZE)
          j = 1;
        else if (p.x >= bounds.x+bounds.width-Scene.HANDLE_SIZE)
          j = 2;
        else j = -1;
        if (p.y <= bounds.y+Scene.HANDLE_SIZE)
          k = 0;
        else if (p.y >= bounds.y+(bounds.height-Scene.HANDLE_SIZE)/2 && 
            p.y <= bounds.y+(bounds.height-Scene.HANDLE_SIZE)/2+Scene.HANDLE_SIZE)
          k = 1;
        else if (p.y >= bounds.y+bounds.height-Scene.HANDLE_SIZE)
          k = 2;
        else k = -1;
        if (k == 0)
        {
          moveToGrid(e);
          activeTool.mousePressedOnHandle(e, this, sel[i], j);
          return;
        }
        if (j == 0 && k == 1)
        {
          moveToGrid(e);
          activeTool.mousePressedOnHandle(e, this, sel[i], 3);
          return;
        }
        if (j == 2 && k == 1)
        {
          moveToGrid(e);
          activeTool.mousePressedOnHandle(e, this, sel[i], 4);
          return;
        }
        if (k == 2)
        {
          moveToGrid(e);
          activeTool.mousePressedOnHandle(e, this, sel[i], j+5);
          return;
        }
      }
      moveToGrid(e);
      dragging = false;
      if ((activeTool.whichClicks() & EditingTool.OBJECT_CLICKS) != 0)
        activeTool.mousePressedOnObject(e, this, sel[i]);
      else
        sentClick = false;
      return;
    }
    
    // The click was not on a selected object.  See whether it was on an unselected object.
    // If so, select it.  If appropriate, send an event to the current tool.
    
    // If the click was on top of multiple objects, the conventional thing to do is to select
    // the closest one.  I'm trying something different: select the smallest one.  This
    // should make it easier to select small objects which are surrounded by larger objects.
    // I may decide to change this, but it seemed like a good idea at the time...
    
    j = -1;
    minarea = Integer.MAX_VALUE;
    for (i = 0; i < theScene.getNumObjects(); i++)
    {
      info = theScene.getObject(i);
      if (info.visible)
      {
        theCamera.setObjectTransform(info.coords.fromLocal());
        bounds = theCamera.findScreenBounds(info.getBounds());
        if (bounds != null && pointInRectangle(p, bounds))
          if (bounds.width*bounds.height < minarea)
          {
            j = i;
            minarea = bounds.width*bounds.height;
          }
      }
    }
    if (j > -1)
    {
      info = theScene.getObject(j);
      if (!e.isShiftDown())
      {
        if (parentFrame instanceof LayoutWindow)
          ((LayoutWindow) parentFrame).clearSelection();
        else
          theScene.clearSelection();
      }
      if (parentFrame instanceof LayoutWindow)
      {
        parentFrame.setUndoRecord(new UndoRecord(parentFrame, false, UndoRecord.SET_SCENE_SELECTION, new Object [] {sel}));
        ((LayoutWindow) parentFrame).addToSelection(j);
      }
      else
        theScene.addToSelection(j);
      parentFrame.updateMenus();
      parentFrame.updateImage();
      moveToGrid(e);
      if ((activeTool.whichClicks() & EditingTool.OBJECT_CLICKS) != 0 && !e.isShiftDown())
        activeTool.mousePressedOnObject(e, this, j);
      else
        sentClick = false;
      clickedObject = info;
      return;
    }
    
    // The click was not on any object.  Start dragging a selection box.
    
    moveToGrid(e);
    draggingSelectionBox = true;
    beginDraggingBox(p, false);
    sentClick = false;
  }

  /**
   * Determine whether a Point falls inside a Rectangle.  This method allows a 1 pixel tolerance
   * to make it easier to click on very small objects.
   */

  private boolean pointInRectangle(Point p, Rectangle r)
  {
    return (r.x-1 <= p.x && r.y-1 <= p.y && r.x+r.width+1 >= p.x && r.y+r.height+1 >= p.y);
  }

  protected void mouseDragged(WidgetMouseEvent e)
  {
    moveToGrid(e);
    if (!dragging)
    {
      Point p = e.getPoint();
      if (Math.abs(p.x-clickPoint.x) < 2 && Math.abs(p.y-clickPoint.y) < 2)
        return;
    }
    dragging = true;    
    deselect = -1;
    if (draggingBox)
    {
      // We are dragging a box, so erase and redraw it.

      if (dragPoint != null)
        drawDraggedShape(new Rectangle(Math.min(clickPoint.x, dragPoint.x), Math.min(clickPoint.y, dragPoint.y), 
            Math.abs(dragPoint.x-clickPoint.x), Math.abs(dragPoint.y-clickPoint.y)));
      dragPoint = e.getPoint();
      if (squareBox)
      {
        if (Math.abs(dragPoint.x-clickPoint.x) > Math.abs(dragPoint.y-clickPoint.y))
        {
          if (dragPoint.y < clickPoint.y)
            dragPoint.y = clickPoint.y - Math.abs(dragPoint.x-clickPoint.x);
          else
            dragPoint.y = clickPoint.y + Math.abs(dragPoint.x-clickPoint.x);
        }
        else
        {
          if (dragPoint.x < clickPoint.x)
            dragPoint.x = clickPoint.x - Math.abs(dragPoint.y-clickPoint.y);
          else
            dragPoint.x = clickPoint.x + Math.abs(dragPoint.y-clickPoint.y);
        }
      }
      drawDraggedShape(new Rectangle(Math.min(clickPoint.x, dragPoint.x), Math.min(clickPoint.y, dragPoint.y), 
              Math.abs(dragPoint.x-clickPoint.x), Math.abs(dragPoint.y-clickPoint.y)));
    }

    // Send the event to the current tool, if appropriate.

    if (sentClick)
      activeTool.mouseDragged(e, this);
  }

  protected void mouseReleased(WidgetMouseEvent e)
  {
    Rectangle r, b;
    int j, sel[] = theScene.getSelection();
    ObjectInfo info;

    moveToGrid(e);

    // Send the event to the current tool, if appropriate.

    if (sentClick)
    {
      if (!dragging)
      {
        Point p = e.getPoint();
        e.translatePoint(clickPoint.x-p.x, clickPoint.y-p.y);
      }
      activeTool.mouseReleased(e, this);
    }

    // If the user was dragging a selection box, then select anything it intersects.
    
    int oldSelection[] = theScene.getSelection();
    if (draggingSelectionBox)
    {
      dragPoint = e.getPoint();
      r = new Rectangle(Math.min(clickPoint.x, dragPoint.x), Math.min(clickPoint.y, dragPoint.y), 
              Math.abs(dragPoint.x-clickPoint.x), Math.abs(dragPoint.y-clickPoint.y));
      if (!e.isShiftDown())
      {
        if (parentFrame instanceof LayoutWindow)
          ((LayoutWindow) parentFrame).clearSelection();
        else
          theScene.clearSelection();
        parentFrame.updateMenus();
      }
      for (int i = 0; i < theScene.getNumObjects(); i++)
      {
        info = theScene.getObject(i);
        if (info.visible)
        {
          theCamera.setObjectTransform(info.coords.fromLocal());
          b = theCamera.findScreenBounds(info.getBounds());
          if (b != null && b.x < r.x+r.width && b.y < r.y+r.height && r.x < b.x+b.width && r.y < b.y+b.height)
          {
            if (!e.isShiftDown())
            {
              if (parentFrame instanceof LayoutWindow)
                ((LayoutWindow) parentFrame).addToSelection(i);
              else
                theScene.addToSelection(i);
              parentFrame.updateMenus();
            }
            else
            {
              for (j = 0; j < sel.length && sel[j] != i; j++);
              if (j == sel.length)
              {
                if (parentFrame instanceof LayoutWindow)
                  ((LayoutWindow) parentFrame).addToSelection(i);
                else
                  theScene.addToSelection(i);
                parentFrame.updateMenus();
              }
            }
          }
        }
      }
      if (currentTool.hilightSelection())
        parentFrame.updateImage();
    }
    draggingBox = draggingSelectionBox = false;

    // If the user shift-clicked a selected object and released the mouse without dragging,
    // then deselect the point.

    if (deselect > -1)
    {
      info = theScene.getObject(deselect);
      if (parentFrame instanceof LayoutWindow)
        ((LayoutWindow) parentFrame).removeFromSelection(deselect);
      else
        theScene.removeFromSelection(deselect);
      parentFrame.updateMenus();
      parentFrame.updateImage();
    }
    
    // If the selection changed, set an undo record.
    
    int newSelection[] = theScene.getSelection();
    boolean changed = (oldSelection.length != newSelection.length);
    for (int i = 0; i < newSelection.length && !changed; i++)
      changed |= (oldSelection[i] != newSelection[i]);
    if (changed)
     parentFrame.setUndoRecord(new UndoRecord(parentFrame, false, UndoRecord.SET_SCENE_SELECTION, new Object [] {oldSelection}));
  }
  
  /** Double-clicking on object should bring up its editor. */
  
  public void mouseClicked(MouseClickedEvent e)
  {
    if (e.getClickCount() == 2 && (activeTool.whichClicks() & EditingTool.OBJECT_CLICKS) != 0 && clickedObject != null && clickedObject.object.isEditable())
    {
      final Object3D obj = clickedObject.object;
      parentFrame.setUndoRecord(new UndoRecord(parentFrame, false, UndoRecord.COPY_OBJECT, new Object [] {obj, obj.duplicate()}));
      obj.edit(parentFrame, clickedObject,  new Runnable() {
	  public void run()
	  {
	    theScene.objectModified(obj);
	    parentFrame.updateImage();
	    parentFrame.updateMenus();
	  }
	} );
    }
  }

  /** Turn the grid on or off when the perspective changes. */
  
  protected void choiceChanged(WidgetEvent ev)
  {
    if (snapToGrid)
      theCamera.setGrid(gridSpacing/gridSubdivisions);
    else
      theCamera.setGrid(0.0);
    super.choiceChanged(ev);
  }
}