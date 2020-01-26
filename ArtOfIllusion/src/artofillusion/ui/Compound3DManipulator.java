/* Copyright (C) 2006-2009 by Francois Guillet and Peter Eastman
   Changes copyright (C) 2020 by Petri Ihalainen

   This program is free software; you can redistribute it and/or modify it under the
   terms of the GNU General Public License as published by the Free Software
   Foundation; either version 2 of the License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful, but WITHOUT ANY
   WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
   PARTICULAR PURPOSE.  See the GNU General Public License for more details. */

package artofillusion.ui;

import buoy.event.*;
import artofillusion.*;
import artofillusion.math.*;
import artofillusion.object.SceneCamera;

import javax.imageio.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;

/**
 * This class displays a set of curves and handles around a selection in a ViewerCanvas.  It processes
 * mouse clicks on them, and translates them into higher level events which are dispatched for
 * further processing, most often by an EditingTool.  It presents a composite user interface
 * for performing moving, scaling, and rotating operations.
 */

public class Compound3DManipulator extends EventSource implements Manipulator
{
  private Rectangle bounds;
  private Rectangle[] boxes;
  private Rectangle extraUVBox;
  private Axis boxAxis[];
  private HandleType boxHandleType[];
  private BoundingBox selectionBounds;
  private Axis dragAxis;
  private HandleType dragHandleType;
  private boolean dragging;
  private Point baseClick;
  private Vec3 dragStartPosition;
  private Vec3 xaxis, yaxis, zaxis;
  private Vec2 x2DaxisNormed, y2DaxisNormed, z2DaxisNormed, screenX, screenY, screenZ, axisCenter;
  private Mat4 worldToScreen;
  private double len, handleSize;
  private Vec3 pqnModeAxes[];
  private int rotSegment;
  private double rotAngle;
  private Point centerPoint;
  private Vec3 center;
  private double axisLength, orAxisLength;
  private RotationHandle[] xyzRotHandles;
  private RotationHandle[] pqnRotHandles;
  private RotationHandle[] uvRotationHandle;
  private RotationHandle[] activeRotationHandleSet;
  private RotationHandle currentRotationHandle;
  private ViewMode viewMode;
  private boolean rotateAroundSelectionCenter = true;

  public final static ViewMode XYZ_MODE = new ViewMode();
  public final static ViewMode UV_MODE = new ViewMode();
  public final static ViewMode PQN_MODE = new ViewMode();

  /** @deprecated Redirects to PQN_MODE */
  @Deprecated
  public final static ViewMode NPQ_MODE = PQN_MODE;

  private static final int HANDLE_SIZE = 12;

  public final static short X_MOVE_INDEX = 0;
  public final static short X_SCALE_INDEX = 1;
  public final static short Y_MOVE_INDEX = 2;
  public final static short Y_SCALE_INDEX = 3;
  public final static short Z_MOVE_INDEX = 4;
  public final static short Z_SCALE_INDEX = 5;
  public final static short CENTER_INDEX = 6;
  public final static short ROTATE_INDEX = 7;
  public final static short TOOL_HANDLE = 8;
  public final static short UV_EXTRA_INDEX = 9;

  public static final Axis X = new Axis("x");
  public static final Axis Y = new Axis("y");
  public static final Axis Z = new Axis("z");
  public static final Axis U = new Axis("u");
  public static final Axis V = new Axis("v");
  public static final Axis W = new Axis("w");
  public static final Axis UV = new Axis("uv");
  public static final Axis P = new Axis("p");
  public static final Axis Q = new Axis("q");
  public static final Axis N = new Axis("n");
  public static final Axis ALL = new Axis("all");

  public static final HandleType MOVE = new HandleType();
  public static final HandleType ROTATE = new HandleType();
  public static final HandleType SCALE = new HandleType();

  private static final Image centerhandle;
  private static final Image xyzHandleImages[] = new Image[6];
  private static final Image uvHandleImages[] = new Image[4];
  private static final Image pqnHandleImages[] = new Image[6];
  
  private static Color handleRed   = new Color(255-16,0,0);
  private static Color handleGreen = new Color(0,255-16,0);
  private static Color handleBlue  = new Color(15,127-16,255);

  static
  {
    xyzHandleImages[X_MOVE_INDEX] = loadImage("xhandle.gif");
    xyzHandleImages[X_SCALE_INDEX] = loadImage("xscale.gif");
    xyzHandleImages[Y_MOVE_INDEX] = loadImage("yhandle.gif");
    xyzHandleImages[Y_SCALE_INDEX] = loadImage("yscale.gif");
    xyzHandleImages[Z_MOVE_INDEX] = loadImage("zhandle.gif");
    xyzHandleImages[Z_SCALE_INDEX] = loadImage("zscale.gif");
    uvHandleImages[X_MOVE_INDEX] = loadImage("uhandle.gif");
    uvHandleImages[X_SCALE_INDEX] = loadImage("uvscale.gif");
    uvHandleImages[Y_MOVE_INDEX] = loadImage("vhandle.gif");
    uvHandleImages[Y_SCALE_INDEX] = loadImage("uvscale.gif");
    centerhandle = loadImage("centerhandle.gif");
    pqnHandleImages[X_MOVE_INDEX] = loadImage("phandle.gif");
    pqnHandleImages[X_SCALE_INDEX] = loadImage("xscale.gif");
    pqnHandleImages[Y_MOVE_INDEX] = loadImage("qhandle.gif");
    pqnHandleImages[Y_SCALE_INDEX] = loadImage("yscale.gif");
    pqnHandleImages[Z_MOVE_INDEX] = loadImage("nhandle.gif");
    pqnHandleImages[Z_SCALE_INDEX] = loadImage("zscale.gif");
  }

  private static Image loadImage(String name)
  {
    try
    {
      return ImageIO.read(NinePointManipulator.class.getResource("/artofillusion/ui/manipulatorIcons/"+name));
    }
    catch (IOException ex)
    {
      ex.printStackTrace();
    }
    return null;
  }

  /**
   * Create a new manipulator.
   */

  public Compound3DManipulator()
  {
    xaxis = Vec3.vx();
    yaxis = Vec3.vy();
    zaxis = Vec3.vz();
    xyzRotHandles = new RotationHandle[3];
    boxes = new Rectangle[7];
    extraUVBox = new Rectangle();
    for (int i = 0; i < boxes.length; ++i)
      boxes[i] = new Rectangle(0,0,HANDLE_SIZE, HANDLE_SIZE);
    extraUVBox = new Rectangle(0,0,HANDLE_SIZE, HANDLE_SIZE);
    boxHandleType = new HandleType[] {MOVE, SCALE, MOVE, SCALE, MOVE, SCALE, MOVE};
    xyzRotHandles[0] = new RotationHandle(64, X, handleBlue);
    xyzRotHandles[1] = new RotationHandle(64, Y, handleGreen);
    xyzRotHandles[2] = new RotationHandle(64, Z, handleRed);
    uvRotationHandle = new RotationHandle[1];
    uvRotationHandle[0] = new RotationHandle(64, U, Color.orange);
    pqnRotHandles = new RotationHandle[3];
    pqnRotHandles[0] = new RotationHandle(64, P, handleBlue);
    pqnRotHandles[1] = new RotationHandle(64, Q, handleGreen);
    pqnRotHandles[2] = new RotationHandle(64, N, handleRed);
    axisLength = 80;
    setViewMode(XYZ_MODE);
  }

  /**
   * Get the current view mode.
   */

  public ViewMode getViewMode()
  {
    return viewMode;
  }

  /**
   * Set the view mode.
   */

  public void setViewMode(ViewMode mode)
  {
    viewMode = mode;
    if (mode == XYZ_MODE)
      boxAxis = new Axis[] {X, X, Y, Y, Z, Z, ALL};
    else if (mode == UV_MODE)
      boxAxis = new Axis[] {U, U, V, V, null, null, ALL}; // W, W !!!
    else
      boxAxis = new Axis[] {P, P, Q, Q, N, N, ALL};
  }

  /**
   * Get whether rotations should be performed around the select center or around the origin.
   */

  public boolean getRotateAroundSelectionCenter()
  {
    return rotateAroundSelectionCenter;
  }

  /**
   * Set whether rotations should be performed around the select center or around the origin.
   */

  public void setRotateAroundSelectionCenter(boolean rotateAroundSelectionCenter)
  {
    this.rotateAroundSelectionCenter = rotateAroundSelectionCenter;
  }

  /**
   * @deprecated
   * Use setPQNAxes. This method redirects.
   * x -> p, y -> q, z -> n. N stands for 'normal-direction'
   */
  @Deprecated
  public void setNPQAxes(Vec3 nDir, Vec3 pDir, Vec3 qDir)
  {
    setPQNAxes(pDir, qDir, nDir);
  }

  /**
   * Set the axis directions to be used in PQN mode.
   */

  public void setPQNAxes(Vec3 pDir, Vec3 qDir, Vec3 nDir)
  {
    pqnModeAxes = new Vec3[] {new Vec3(pDir), new Vec3(qDir), new Vec3(nDir)};
    pqnRotHandles[0].setAxis(pDir, qDir);
    pqnRotHandles[1].setAxis(qDir, nDir);
    pqnRotHandles[2].setAxis(nDir, pDir);
  }

  /**
   * Get the direction of a particular axis.
   */

  public Vec3 getAxisDirection(Axis axis, ViewerCanvas view)
  {
    if (axis == X)
      return Vec3.vx();
    if (axis == Y)
      return Vec3.vy();
    if (axis == Z)
      return Vec3.vz();
    if (axis == U)
    {
      CoordinateSystem coords = view.getCamera().getCameraCoordinates();
      return coords.getZDirection().cross(coords.getUpDirection());
    }
    if (axis == V)
    {
      CoordinateSystem coords = view.getCamera().getCameraCoordinates();
      return coords.getUpDirection();
    }
    if (axis == W)
    {
      CoordinateSystem coords = view.getCamera().getCameraCoordinates();
      return coords.getZDirection().times(-1.0);
    }
    if (axis == P)
      return pqnModeAxes[0];
    if (axis == Q)
      return pqnModeAxes[1];
    if (axis == N)
      return pqnModeAxes[2];
    throw new IllegalArgumentException("Axis "+axis.getName()+" does not have a fixed direction");
  }

  /**
   * Get the type of handle which is displayed at a location.
   */

  public HandleType getHandleTypeAtLocation(Point location, ViewerCanvas view, BoundingBox selectionBounds)
  {
    if (selectionBounds == null)
      return null;
    Vec3 center = view.getCamera().getViewToWorld().times(selectionBounds.getCenter());
    findHandleLocations(center, view);
    for (int i = 0; i < boxes.length; i++)
    {
      if (viewMode == UV_MODE && (i == Z_MOVE_INDEX || i == Z_SCALE_INDEX))
        continue;
      if (boxes[i].contains(location))
        return boxHandleType[i];
    }
    RotationHandle[] rotHandles = null;
    if (viewMode == XYZ_MODE)
      rotHandles = xyzRotHandles;
    else if (viewMode == UV_MODE)
      rotHandles = uvRotationHandle;
    else if (viewMode == PQN_MODE)
      rotHandles = pqnRotHandles;
    //and detect if click happened in one of them
    for (int i = 0; i < rotHandles.length; i++)
      if ((rotSegment = rotHandles[i].findClickTarget(location, view.getCamera())) != -1)
        return ROTATE;
    //check for extra UV handle
    if (viewMode == UV_MODE && extraUVBox.contains(location))
      return SCALE;
    return null;
  }

  /**
   * Find the locations of all handles.
   */

  private void findHandleLocations(Vec3 center, ViewerCanvas view)
  {
    // Which axes are active?
    if (viewMode == XYZ_MODE)
    {
      xaxis = Vec3.vx();
      yaxis = Vec3.vy();
      zaxis = Vec3.vz();
    }
    else if (viewMode == UV_MODE)
    {
     // Let's have this in screen coordinates. 
      CoordinateSystem coords = view.getCamera().getCameraCoordinates();
      xaxis = coords.getZDirection().cross(coords.getUpDirection());
      yaxis = coords.getUpDirection();
      zaxis = coords.getZDirection().times(-1);
    }
    else
    {
      xaxis = pqnModeAxes[0];
      yaxis = pqnModeAxes[1];
      zaxis = pqnModeAxes[2];
    }

    // How large the manipulator and the handles would be in the scene
    if (view.isPerspective())
    {
      double projectionDist = calculateProjectionDistance(view);
      handleSize = HANDLE_SIZE*view.getCamera().getWorldToView().times(center).z/projectionDist/100.0;
      len = axisLength*view.getDistToPlane()/projectionDist/100.0;
    }
    else
    {
      handleSize = HANDLE_SIZE / view.getScale();
      len = axisLength / view.getScale();
    }
    worldToScreen = view.getCamera().getWorldToScreen();

    Vec3 xpos = center.plus(xaxis.times(len));
    Vec3 ypos = center.plus(yaxis.times(len));
    Vec3 zpos = center.plus(zaxis.times(len));
    Vec3 xHandlePos = center.plus(xaxis.times(len + handleSize));
    Vec3 yHandlePos = center.plus(yaxis.times(len + handleSize));
    Vec3 zHandlePos = center.plus(zaxis.times(len + handleSize));
    Vec3 xHandleOffset = center.plus(xaxis.times(len + handleSize*1.5));
    Vec3 yHandleOffset = center.plus(yaxis.times(len + handleSize*1.5));
    Vec3 zHandleOffset = center.plus(zaxis.times(len + handleSize*1.5));
    Vec2 x2DHandleOffset = worldToScreen.timesXY(xHandleOffset);
    Vec2 y2DHandleOffset = worldToScreen.timesXY(yHandleOffset);
    Vec2 z2DHandleOffset = worldToScreen.timesXY(zHandleOffset);
    axisCenter = worldToScreen.timesXY(center);
    screenX = worldToScreen.timesXY(xpos);
    screenY = worldToScreen.timesXY(ypos);
    screenZ = worldToScreen.timesXY(zpos);
    x2DHandleOffset.subtract(screenX);
    y2DHandleOffset.subtract(screenY);
    z2DHandleOffset.subtract(screenZ);
    Vec2 screenXHandle = worldToScreen.timesXY(xHandlePos);
    Vec2 screenYHandle = worldToScreen.timesXY(yHandlePos);
    Vec2 screenZHandle = worldToScreen.timesXY(zHandlePos);
    x2DaxisNormed = screenX.minus(axisCenter).unit();
    y2DaxisNormed = screenY.minus(axisCenter).unit();
    z2DaxisNormed = screenZ.minus(axisCenter).unit();
    centerPoint = new Point((int) Math.round(axisCenter.x), (int) Math.round(axisCenter.y));
    boxes[CENTER_INDEX].x = (int)(centerPoint.x - HANDLE_SIZE/2);
    boxes[CENTER_INDEX].y = (int)(centerPoint.y - HANDLE_SIZE/2);
    for (int i = 0; i < 2; i++)
    {
      boxes[X_MOVE_INDEX +i].x = (int)( screenXHandle.x - HANDLE_SIZE/2  + i * x2DHandleOffset.x);
      boxes[X_MOVE_INDEX +i].y = (int)( screenXHandle.y - HANDLE_SIZE/2  + i * x2DHandleOffset.y);
    }
    for (int i = 0; i < 2; i++)
    {
      boxes[Y_MOVE_INDEX +i].x = (int)( screenYHandle.x - HANDLE_SIZE/2  + i * y2DHandleOffset.x);
      boxes[Y_MOVE_INDEX +i].y = (int)( screenYHandle.y - HANDLE_SIZE/2  + i * y2DHandleOffset.y);
    }
    if (viewMode != UV_MODE)
      for (int i = 0; i < 2; i++)
      {
        boxes[Z_MOVE_INDEX +i].x = (int)( screenZHandle.x - HANDLE_SIZE/2  + i * z2DHandleOffset.x);
        boxes[Z_MOVE_INDEX +i].y = (int)( screenZHandle.y - HANDLE_SIZE/2  + i * z2DHandleOffset.y);
      }
    else
    {
      extraUVBox.x = boxes[X_SCALE_INDEX].x;
      extraUVBox.y = boxes[Y_SCALE_INDEX].y;
    }
    if (viewMode == XYZ_MODE)
      activeRotationHandleSet = xyzRotHandles;
    else if (viewMode == UV_MODE)
    {
      activeRotationHandleSet = uvRotationHandle;
      activeRotationHandleSet[0].setAxis(zaxis, xaxis);
    }
    else if (viewMode == PQN_MODE)
      activeRotationHandleSet = pqnRotHandles;
    for (int i = 0; i < activeRotationHandleSet.length; ++i)
    {
      RotationHandle rotHandle = activeRotationHandleSet[i];
      for (int j = 0; j < rotHandle.points3d.length; j++)
        rotHandle.points2d[j] = worldToScreen.timesXY(center.plus(rotHandle.points3d[j].times(len)));
    }
  }

  /**
   * Draw the handles onto a ViewerCanvas.
   *
   * @param view             the canvas onto which to draw the handles
   * @param selectionBounds  a BoundingBox enclosing whatever is selected in the canvas
   */

  @Override
  public void draw(ViewerCanvas view, BoundingBox selectionBounds)
  {
    if (selectionBounds == null)
    {
      //not a valid selection, do not draw onto screen
      return;
    }
    bounds = findScreenBounds(selectionBounds, view.getCamera());

    //when in PQN mode, the manipulator must not change position during rotation or scaling
    boolean freezeManipulator = (dragging && (dragHandleType == ROTATE || dragHandleType == SCALE));
    if (!freezeManipulator)
      center = view.getCamera().getViewToWorld().times(selectionBounds.getCenter());

    findHandleLocations(center, view);

    //draw rotation feedback if appropriate
    if (dragging && dragHandleType == ROTATE)
    {
      Vec3[] pt = currentRotationHandle.getRotationFeedback(rotAngle);
      Vec2 pt2d;
      int[] x = new int[pt.length];
      int[] y = new int[pt.length];
      for (int j = 0; j < pt.length; j++)
      {
        pt2d = worldToScreen.timesXY(center.plus(pt[j].times(len)));
        x[j] = (int) pt2d.x;
        y[j] = (int) pt2d.y;
      }
      Polygon p = new Polygon(x, y, x.length);
      view.drawShape(p, Color.darkGray);
      view.fillShape( p, Color.gray);
    }

    // Draw the axes.
    Color xColor, yColor, zColor;
    Image[] handles = null;
    if (viewMode == XYZ_MODE)
    {
      xColor = handleBlue;
      yColor = handleGreen;
      zColor = handleRed;
      handles = xyzHandleImages;
    }
    else if (viewMode == UV_MODE)
    {
      xColor = Color.orange;
      yColor = Color.orange;
      zColor = Color.orange;
      handles = uvHandleImages;
    }
    else
    {
      xColor = handleBlue;
      yColor = handleGreen;
      zColor = handleRed;
      handles = pqnHandleImages;
    }
    view.drawLine(centerPoint, new Point((int) screenX.x, (int) screenX.y), xColor);
    view.drawLine(centerPoint, new Point((int) screenY.x, (int) screenY.y), yColor);
    view.drawLine(centerPoint, new Point((int) screenZ.x, (int) screenZ.y), zColor);

    // Draw the handles.
    view.drawImage(centerhandle, boxes[CENTER_INDEX].x, boxes[CENTER_INDEX].y);
    for (int i = 0; i < 2; i++)
    {
      view.drawImage(handles[X_MOVE_INDEX +i], boxes[X_MOVE_INDEX +i].x, boxes[X_MOVE_INDEX +i].y);
      view.drawImage(handles[Y_MOVE_INDEX +i], boxes[Y_MOVE_INDEX +i].x, boxes[Y_MOVE_INDEX +i].y);
      if (viewMode != UV_MODE)
        view.drawImage(handles[Z_MOVE_INDEX +i], boxes[Z_MOVE_INDEX +i].x, boxes[Z_MOVE_INDEX +i].y);
      else
        view.drawImage(handles[X_SCALE_INDEX], extraUVBox.x, extraUVBox.y);
    }

    //draw the rotation handles
    for (int i = 0; i < activeRotationHandleSet.length; ++i)
    {
      RotationHandle rotHandle = activeRotationHandleSet[i];
      for (int j = 0; j < rotHandle.points2d.length-1; j++)
        view.drawLine(new Point((int) rotHandle.points2d[j].x,   (int) rotHandle.points2d[j].y),
                      new Point((int) rotHandle.points2d[j+1].x, (int) rotHandle.points2d[j+1].y), 
                      rotHandle.color);
    }
  }

  @Override
  public boolean mousePressed(WidgetMouseEvent ev, ViewerCanvas view, BoundingBox selectionBounds)
  {
    Rectangle r = findScreenBounds(selectionBounds, view.getCamera());

    //3D manipulators don't draw the bounds, but bounds is used to detect
    //a valid selection

    if (r == null)
        return false;
    bounds = r;
    this.selectionBounds = selectionBounds;
    center = view.getCamera().getViewToWorld().times(selectionBounds.getCenter());
    findHandleLocations(center, view);
    Point p = ev.getPoint();
    for (int i = 6; i >= 0; i--)
    {
      if (viewMode == UV_MODE && ( i == Z_MOVE_INDEX || i == Z_SCALE_INDEX) )
        continue;
      if (boxes[i].contains(p))
      {
        if (i == CENTER_INDEX)
        {
          dragAxis = ALL;
          dragHandleType = MOVE;
          dragStartPosition = center;
        }
        else
        {
          dragHandleType = boxHandleType[i];
          dragAxis = boxAxis[i];
        }
        orAxisLength = axisLength;
        dragging = true;
        baseClick = new Point(ev.getPoint());
        dispatchEvent(new HandlePressedEvent(view, dragHandleType, dragAxis, bounds, selectionBounds, ev));
        return true;
      }
    }
    //select proper rotation handles
    RotationHandle[] rotHandles = null;
    if (viewMode == XYZ_MODE)
      rotHandles = xyzRotHandles;
    else if (viewMode == UV_MODE)
      rotHandles = uvRotationHandle;
    else if (viewMode == PQN_MODE)
      rotHandles = pqnRotHandles;
    //and detect if click happened in one of them
    for (int i = 0; i < rotHandles.length; i++)
    {
      if ( (rotSegment = rotHandles[i].findClickTarget(p, view.getCamera())) != -1)
      {
        currentRotationHandle = rotHandles[i];
        dragHandleType = ROTATE;
        dragAxis = currentRotationHandle.axis;
        dragging = true;
        baseClick = new Point(ev.getPoint());
        dispatchEvent(new HandlePressedEvent(view, dragHandleType, dragAxis, bounds, selectionBounds, ev));
        rotAngle = 0;
        return true;
      }
    }
    //check for extra UV handle
    if (viewMode == UV_MODE && extraUVBox.contains(p))
    {
      dragHandleType = SCALE;
      dragAxis = UV;
      orAxisLength = axisLength;
      dragging = true;
      baseClick = new Point(ev.getPoint());
      dispatchEvent(new HandlePressedEvent(view, dragHandleType, dragAxis, bounds, selectionBounds, ev));
      return true;
    }
    return false;
  }

  // This is for the center handle, when handling a mesh
  
  public void mousePressedOnHandle(WidgetMouseEvent ev, ViewerCanvas view, BoundingBox selectionBounds, Vec3 handleLocation)
  {
    center = view.getCamera().getViewToWorld().times(selectionBounds.getCenter());
    dragHandleType = MOVE;
    dragAxis = ALL;
    dragStartPosition = handleLocation;
    dragging = true;
    baseClick = new Point(ev.getPoint());
    dispatchEvent(new HandlePressedEvent(view, dragHandleType, dragAxis, bounds, selectionBounds, ev));
  }

  @Override
  public void mouseDragged(WidgetMouseEvent ev, ViewerCanvas view)
  {
    if (!dragging)
      return;
    findHandleLocations(center, view);
    if (dragHandleType == MOVE)
      moveDragged(ev, view);
    else if (dragHandleType == ROTATE)
      rotateDragged(ev, view);
    else
      scaleDragged(ev, view);
  }

  private void moveDragged(WidgetMouseEvent ev, ViewerCanvas view)
  {
    boolean isShiftDown = ev.isShiftDown();
    double gridSize = view.getGridSpacing()/view.getSnapToSubdivisions();
    
    // Using the center handle
    // Movement is parallel to screen plane

    if (dragAxis == ALL)
    {
      Vec2 disp = new Vec2(ev.getPoint().x - baseClick.x, ev.getPoint().y - baseClick.y );
      Vec3 drag;
      if (ev.isControlDown())
        drag = view.getCamera().getCameraCoordinates().getZDirection().times(-disp.y*0.01);
      else
        drag = view.getCamera().findDragVector(dragStartPosition, disp.x, disp.y);
      if (isShiftDown)
      {
        drag.x = gridSize*Math.round(drag.x/gridSize);
        drag.y = gridSize*Math.round(drag.y/gridSize);
        drag.z = gridSize*Math.round(drag.z/gridSize);
      }
      Mat4 transform = Mat4.translation(drag.x, drag.y, drag.z);
      dispatchEvent(new HandleDraggedEvent(view, dragHandleType, dragAxis, bounds, selectionBounds, ev, transform));
      return;
    }

    // Using one of the move handles

    Vec3 dragDir3D;
    Vec2 dragDir2D;

    if (dragAxis == X || dragAxis == U || dragAxis == P)
    {
      dragDir3D = xaxis;
      dragDir2D = x2DaxisNormed;
    }
    else if (dragAxis == Y || dragAxis == V || dragAxis == Q)
    {
      dragDir3D = yaxis;
      dragDir2D = y2DaxisNormed;
    }
    else
    {
      dragDir3D = zaxis;
      dragDir2D = z2DaxisNormed;
    }

    Vec3 camZDir = view.getCamera().getCameraCoordinates().getZDirection();
    Vec3 camOrig = view.getCamera().getCameraCoordinates().getOrigin();
    Vec3 camYDir = view.getCamera().getCameraCoordinates().getUpDirection();
    Vec3 camXDir = camZDir.cross(camYDir).unit();

    Vec2 mouseDrag = new Vec2(ev.getPoint().x - baseClick.x, ev.getPoint().y - baseClick.y );
    Vec2 drag2D = dragDir2D.times(mouseDrag.dot(dragDir2D));
    Vec3 dragProjected = camXDir.times(drag2D.x).minus(camYDir.times(drag2D.y));
    Vec3 dirProjected = dragProjected.unit();
    double scaleOut;
    if (view.isPerspective())
    {
      double depth = center.minus(camOrig).dot(camZDir);
      double projDist = calculateProjectionDistance(view);
      scaleOut = depth/projDist/100.0;
    }
    else
      scaleOut = 1.0/view.getScale();
    dragProjected.scale(scaleOut);
    double dragDistance = dragProjected.dot(dragDir3D);
    double axisProjectionScale = Math.abs(dirProjected.dot(dragDir3D));

    // Mouse may have returned to starting line 
    //   --> drag vector length = 0.0
    //   --> axis scale = 0.0

    if (axisProjectionScale != 0.0)
      dragDistance /= axisProjectionScale*axisProjectionScale;

    if (isShiftDown)
    {
      dragDistance /= gridSize;
      dragDistance = Math.round(dragDistance);
      dragDistance *= gridSize;
    }
    Vec3 drag3D = dragDir3D.times(dragDistance);
    Mat4 transform = Mat4.translation(drag3D.x, drag3D.y, drag3D.z);
    dispatchEvent(new HandleDraggedEvent(view, dragHandleType, dragAxis, bounds, selectionBounds, ev, transform));
  }

  private void rotateDragged(WidgetMouseEvent ev, ViewerCanvas view)
  {
    boolean isShiftDown = ev.isShiftDown();

    Vec2 disp = new Vec2(ev.getPoint().x - baseClick.x, ev.getPoint().y - baseClick.y );
    Vec2 vector = currentRotationHandle.points2d[rotSegment+1].minus(currentRotationHandle.points2d[rotSegment]);
    vector.normalize();
    rotAngle = vector.dot(disp)/70;

    if (isShiftDown)
    {
      rotAngle *= (180.0/(5*Math.PI));
      rotAngle = Math.round(rotAngle);
      rotAngle *= (5*Math.PI)/180;
    }
    Mat4 mat = Mat4.axisRotation(currentRotationHandle.rotAxis, rotAngle);
    if (rotateAroundSelectionCenter)
      mat = Mat4.translation(center.x, center.y, center.z).times(mat.times(Mat4.translation(-center.x, -center.y, -center.z)));
    dispatchEvent(new HandleDraggedEvent(view, dragHandleType, dragAxis, bounds, selectionBounds, ev, mat, rotAngle));
  }

  private void scaleDragged(WidgetMouseEvent ev, ViewerCanvas view)
  {
    Point p = ev.getPoint();
    boolean isShiftDown = ev.isShiftDown();
    boolean isCtrlDown = ( ev.getModifiers() & ActionEvent.CTRL_MASK ) != 0;
    double scaleX, scaleY, scaleZ;

    Vec2 base = new Vec2(baseClick.x - centerPoint.x, baseClick.y - centerPoint.y);
    Vec2 current = new Vec2(p.x - centerPoint.x, p.y - centerPoint.y);
    double scale = base.dot(current);
    if (base.length() < 1)
      scale = 1;
    else
      scale /= (base.length()*base.length());
    if (isCtrlDown)
    {
      axisLength = orAxisLength*scale;
      scale = 1;
      view.repaint();
    }
    else
    {
      scaleX = scaleY = scaleZ = 1;
      if (dragAxis == X || dragAxis == U || dragAxis == P)
      {
        scaleX = scale;
        if (isShiftDown)
          scaleY = scaleZ = scaleX;
      }
      else if (dragAxis == Y || dragAxis == V || dragAxis == Q)
      {
        scaleY = scale;
        if (isShiftDown)
          scaleX = scaleZ = scaleY;
      }
      else if (dragAxis == Z || dragAxis == N)
      {
        scaleZ = scale;
        if (isShiftDown)
          scaleY = scaleX = scaleZ;
      }
      else if (dragAxis == UV)
      {
        scaleX = x2DaxisNormed.dot(current)/x2DaxisNormed.dot(base);
        scaleY = y2DaxisNormed.dot(current)/y2DaxisNormed.dot(base);
        if (isShiftDown)
        {
          if (scaleX < 1 && scaleY < 1)
            scaleX = scaleZ = scaleY = Math.min(scaleX, scaleY);
          else
            scaleX = scaleZ = scaleY = Math.max(scaleX, scaleY);
        }
      }
      CoordinateSystem coords = new CoordinateSystem(center, zaxis, yaxis);
      Mat4 m = Mat4.scale(scaleX, scaleY, scaleZ).times(coords.toLocal());
      m = coords.fromLocal().times(m);
      if (dragAxis == UV)
        dispatchEvent(new HandleDraggedEvent(view, dragHandleType, dragAxis, bounds, selectionBounds, ev, m, scaleX, scaleY));
      else
        dispatchEvent(new HandleDraggedEvent(view, dragHandleType, dragAxis, bounds, selectionBounds, ev, m, scale, 0.0));
    }
  }

  @Override
  public void mouseReleased(WidgetMouseEvent ev, ViewerCanvas view)
  {
    if (!dragging)
      return;
    dispatchEvent(new HandleReleasedEvent(view, dragHandleType, dragAxis, bounds, selectionBounds, ev));
    dragging = false;
    dragAxis = null;
    dragHandleType = null;
  }

  /**
   * Given a bounding box in view coordinates, find the corresponding rectangle in
   * screen coordinates.
   */

  public Rectangle findScreenBounds(BoundingBox b, Camera cam)
  {
    Mat4 m = cam.getObjectToWorld();
    cam.setObjectTransform(cam.getViewToWorld());
    Rectangle r = cam.findScreenBounds(b);
    cam.setObjectTransform(m);
    if (r != null)
      r.setBounds(r.x-HANDLE_SIZE, r.y-HANDLE_SIZE, r.width+2*HANDLE_SIZE, r.height+2*HANDLE_SIZE);
    return r;
  }

  /* 
    This is a value that really should be provoded by the view camera object.

    The method should be used, when the view is in perspective mode.
    In parallel mode it just return the distToScreen of Camera.
  */
  
  private double calculateProjectionDistance(ViewerCanvas view)
  {
    if (! view.isPerspective())
      return view.getCamera().getDistToScreen();

    double projectionDist;
    if (view.getBoundCamera() != null && view.getBoundCamera().getObject() instanceof SceneCamera)
    {
      double edgeAngle = (Math.PI - Math.toRadians(((SceneCamera)view.getBoundCamera().getObject()).getFieldOfView()))/2.0;
      projectionDist = Math.tan(edgeAngle)/Camera.DEFAULT_DISTANCE_TO_SCREEN*view.getBounds().height/view.getScale()*10;
    }
    else
      projectionDist = view.getCamera().getDistToScreen();

    return projectionDist;
  }

  /**
   * Instances of this class represent the different view modes for the manipulator.
   */

  public static class ViewMode
  {
    private ViewMode()
    {
    }
  }

  private class RotationHandle
  {
    private Axis axis;
    private int segments;
    protected Color color;
    protected Vec3[] points3d;
    protected Vec2[] points2d;
    protected Vec3 rotAxis, refAxis;

    /**
     * Creates a Rotation Handle with a given number of segments
     *
     * @param segments The number of segmetns that describe the rotation circle
     * @param axis The rotation axis
     */

    public RotationHandle(int segments, Axis axis, Color color )
    {
      this.segments = segments;
      this.color = color;
      this.axis = axis;
      points3d = new Vec3[segments+1];
      points2d = new Vec2[segments+1];
      if (axis == X || axis == U || axis == P)
        setAxis(xaxis, yaxis);
      else if (axis == Y || axis == V || axis == Q)
        setAxis(yaxis, zaxis);
      else
        setAxis(zaxis, xaxis);
    }

    /**
     * sets the axis of the handle
     * @param rotAxis The rotation axis
     * @param refAxis The axis where arc drawing begins.
     * Used when asking for a rotation feedback polygon
     */

    public void setAxis(Vec3 rotAxis, Vec3 refAxis)
    {
      this.rotAxis = rotAxis;
      this.refAxis = refAxis;
      Mat4 m = Mat4.axisRotation(rotAxis, 2*Math.PI/segments);
      Vec3 v = new Vec3(refAxis);
      for (int i = 0; i <= segments; i++)
        points3d[i] = v = m.times(v);
    }

    /**
     * Given an angle, this method returns a 3D polygon which can be used to
     * tell the user the rotation amount when drawn on the canvas
     * @param angle
     * @return The 2d points deinfing the polygon
     */

    public Vec3[] getRotationFeedback(double angle)
    {
      Vec3[] points = new Vec3[segments+1];

      points[0] = new Vec3();
      Mat4 m = null;
      Vec3 v = null;
      m = Mat4.axisRotation(rotAxis, angle/segments);
      v = new Vec3(refAxis);
      points[1] = v;
      for (int i = 1; i < segments; i++)
        points[i+1] = v = m.times(v);
      return points;
    }

    /**
     * This method tells if the mouse has been been clicked on a rotation handle
     *
     * @param pos The point where the mouse was clicked
     * @param camera The view camera
     * @return The number of the segment being clicked on or -1 if the mouse has not been
     * clicked on the handle
     */

    public int findClickTarget(Point pos, Camera camera)
    {
      double u, v, w, z;
      double closestz = Double.MAX_VALUE;
      int which = -1;
      for ( int i = 0; i < points2d.length - 1; i++ )
      {
        Vec2 v1 = points2d[i];
        Vec2 v2 = points2d[i+1];
        if ( ( pos.x < v1.x - HANDLE_SIZE / 4 && pos.x < v2.x - HANDLE_SIZE / 4 ) ||
             ( pos.x > v1.x + HANDLE_SIZE / 4 && pos.x > v2.x + HANDLE_SIZE / 4 ) ||
             ( pos.y < v1.y - HANDLE_SIZE / 4 && pos.y < v2.y - HANDLE_SIZE / 4 ) ||
             ( pos.y > v1.y + HANDLE_SIZE / 4 && pos.y > v2.y + HANDLE_SIZE / 4 ) )
          continue;

        // Determine the distance of the click point from the line.

        if ( Math.abs( v1.x - v2.x ) > Math.abs( v1.y - v2.y ) )
        {
          if ( v2.x > v1.x )
          {
            v = ( (double) pos.x - v1.x ) / ( v2.x - v1.x );
            u = 1.0 - v;
          }
          else
          {
            u = ( (double) pos.x - v2.x ) / ( v1.x - v2.x );
            v = 1.0 - u;
          }
          w = u * v1.y + v * v2.y - pos.y;
        }
        else
        {
          if ( v2.y > v1.y )
          {
            v = ( (double) pos.y - v1.y ) / ( v2.y - v1.y );
            u = 1.0 - v;
          }
          else
          {
            u = ( (double) pos.y - v2.y ) / ( v1.y - v2.y );
            v = 1.0 - u;
          }
          w = u * v1.x + v * v2.x - pos.x;
        }
        if ( Math.abs( w ) > HANDLE_SIZE / 2 )
          continue;
        z = u * camera.getObjectToView().timesZ( points3d[i] ) +
            v * camera.getObjectToView().timesZ( points3d[i+1] );
        if ( z < closestz )
        {
          closestz = z;
          which = i;
        }
      }
      return which;
    }
  }

  /**
   * Instances of this class represent coordinate axes.
   */

  public static class Axis
  {
    private String name;

    private Axis(String name)
    {
      this.name = name;
    }

    /**
     * Get the name of this axis.
     */

    public String getName()
    {
      return name;
    }
  }

  /**
   * Instances of this class represent handle types.
   */

  public static class HandleType
  {
    private HandleType()
    {
    }
  }

  /**
   * This is the superclass of the various events generated by the manipulator.
   */

  public class HandleEvent
  {
    private ViewerCanvas view;
    private Axis axis;
    private HandleType handleType;
    private Rectangle screenBounds;
    private BoundingBox selectionBounds;
    private WidgetMouseEvent event;

    private HandleEvent(ViewerCanvas view, HandleType handleType, Axis axis, Rectangle screenBounds, BoundingBox selectionBounds, WidgetMouseEvent event)
    {
      this.view = view;
      this.handleType = handleType;
      this.axis = axis;
      this.screenBounds = screenBounds;
      this.selectionBounds = selectionBounds;
      this.event = event;
    }

    /**
     * Get the ViewerCanvas in which this event occurred.
     */

    public ViewerCanvas getView()
    {
      return view;
    }

    /**
     * Get the type of handle being manipulated.
     */

    public HandleType getHandleType()
    {
      return handleType;
    }

    /**
     * Get the axis for which the handle is being manipulated.
     */

    public Axis getAxis()
    {
      return axis;
    }

    /**
     * Get the bounding box of the manipulator on screen at the time the mouse was first clicked.
     */

    public Rectangle getScreenBounds()
    {
      return new Rectangle(screenBounds);
    }

    /**
     * Get the bounding box in view coordinates of the selection at the time the mouse was first clicked.
     */

    public BoundingBox getSelectionBounds()
    {
      return new BoundingBox(selectionBounds);
    }

    /**
     * Get the original mouse event responsible for this event being generated.
     */

    public WidgetMouseEvent getMouseEvent()
    {
      return event;
    }

    /**
     * Get the manipulator which generated this event.
     */

    public Compound3DManipulator getManipulator()
    {
      return Compound3DManipulator.this;
    }
  }

  /**
   * This is the event class generated when the user clicks on a handle.
   */

  public class HandlePressedEvent extends HandleEvent
  {
    public HandlePressedEvent(ViewerCanvas view, HandleType handleType, Axis axis, Rectangle screenBounds, BoundingBox selectionBounds, WidgetMouseEvent event)
    {
      super(view, handleType, axis, screenBounds, selectionBounds, event);
    }
  }

  /**
   * This is the event class generated when the user drags on a handle.
   */

  public class HandleDraggedEvent extends HandleEvent
  {
    private Mat4 transform;
    private double angle, scale1, scale2;

    /**
     * Create a HandleDraggedEvent for a MOVE drag.
     */

    public HandleDraggedEvent(ViewerCanvas view, HandleType handleType, Axis axis, Rectangle screenBounds, BoundingBox selectionBounds, WidgetMouseEvent event, Mat4 transform)
    {
      super(view, handleType, axis, screenBounds, selectionBounds, event);
      this.transform = transform;
    }

    /**
     * Create a HandleDraggedEvent for a ROTATE drag.
     */

    public HandleDraggedEvent(ViewerCanvas view, HandleType handleType, Axis axis, Rectangle screenBounds, BoundingBox selectionBounds, WidgetMouseEvent event, Mat4 transform, double angle)
    {
      this(view, handleType, axis, screenBounds, selectionBounds, event, transform);
      this.angle = angle;
    }

    /**
     * Create a HandleDraggedEvent for a SCALE drag.
     */

    public HandleDraggedEvent(ViewerCanvas view, HandleType handleType, Axis axis, Rectangle screenBounds, BoundingBox selectionBounds, WidgetMouseEvent event, Mat4 transform, double scale1, double scale2)
    {
      this(view, handleType, axis, screenBounds, selectionBounds, event, transform);
      this.scale1 = scale1;
      this.scale2 = scale2;
    }

    /**
     * Get a matrix which can be used to transform objects or vertices from their original positions
     * to their moved, scaled, or rotated positions.
     */

    public Mat4 getTransform()
    {
      return transform;
    }

    /**
     * Get the rotation angle in radians, if this is a ROTATE drag.
     */

    public double getRotationAngle()
    {
      return angle;
    }

    /**
     * Get the scale factor for the primary axis, if this is a SCALE drag.
     */

    public double getPrimaryScale()
    {
      return scale1;
    }

    /**
     * Get the scale factor for the second axis, if this is a UV SCALE drag.
     */

    public double getSecondaryScale()
    {
      return scale2;
    }
  }

  /**
   * This is the event class generated when the user releases on a handle.
   */

  public class HandleReleasedEvent extends HandleEvent
  {
    public HandleReleasedEvent(ViewerCanvas view, HandleType handleType, Axis axis, Rectangle screenBounds, BoundingBox selectionBounds, WidgetMouseEvent event)
    {
      super(view, handleType, axis, screenBounds, selectionBounds, event);
    }
  }
}
