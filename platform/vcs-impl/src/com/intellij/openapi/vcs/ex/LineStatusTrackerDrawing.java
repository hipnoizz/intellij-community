/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.ex;

import com.intellij.codeInsight.hint.EditorFragmentComponent;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.diff.DiffColors;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.markup.ActiveGutterRenderer;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.LineMarkerRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vcs.actions.ShowNextChangeMarkerAction;
import com.intellij.openapi.vcs.actions.ShowPrevChangeMarkerAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredSideBorder;
import com.intellij.ui.HintHint;
import com.intellij.ui.HintListener;
import com.intellij.ui.LightweightHint;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EventObject;
import java.util.List;

/**
 * @author irengrig
 */
public class LineStatusTrackerDrawing {
  private LineStatusTrackerDrawing() {
  }

  static TextAttributes getAttributesFor(final Range range) {
    final Color stripeColor = getDiffColor(range);
    final TextAttributes textAttributes = new TextAttributes(null, stripeColor, null, EffectType.BOXED, Font.PLAIN);
    textAttributes.setErrorStripeColor(stripeColor);
    return textAttributes;
  }

  private static void paintGutterFragment(final Editor editor, final Graphics g, final Rectangle r, final Range range) {
    final EditorGutterComponentEx gutter = ((EditorEx)editor).getGutterComponentEx();
    Color stripeColor = getDiffGutterColor(range);

    int triangle = 4;
    if (range.getInnerRanges() == null) { // actual painter
      g.setColor(stripeColor);

      final int endX = gutter.getWhitespaceSeparatorOffset();
      final int x = r.x + r.width - 3;
      final int width = endX - x;
      if (r.height > 0) {
        g.fillRect(x, r.y, width, r.height);
      }
      else {
        final int[] xPoints = new int[]{x, x, endX};
        final int[] yPoints = new int[]{r.y - triangle, r.y + triangle, r.y};
        g.fillPolygon(xPoints, yPoints, 3);
      }
    }
    else { // registry: diff.status.tracker.smart
      final int x = gutter.getLineMarkerAreaOffset() + gutter.getIconsAreaWidth() + 1;
      final int endX = gutter.getWhitespaceSeparatorOffset();
      final int width = endX - x;

      if (range.getType() == Range.DELETED) {
        final int y = lineToY(editor, range.getLine1());

        final int[] xPoints = new int[]{x, x, endX + 1};
        final int[] yPoints = new int[]{y - triangle, y + triangle, y};

        g.setColor(stripeColor);
        g.fillPolygon(xPoints, yPoints, 3);

        g.setColor(gutter.getOutlineColor(false));
        g.drawPolygon(xPoints, yPoints, 3);
      }
      else {
        int y = lineToY(editor, range.getLine1());
        int endY = lineToY(editor, range.getLine2());

        List<Range.InnerRange> innerRanges = range.getInnerRanges();
        for (Range.InnerRange innerRange : innerRanges) {
          if (innerRange.getType() == Range.DELETED) continue;

          int start = lineToY(editor, innerRange.getLine1());
          int end = lineToY(editor, innerRange.getLine2());

          g.setColor(getDiffColor(innerRange));
          g.fillRect(x, start, width, end - start);
        }

        for (int i = 0; i < innerRanges.size(); i++) {
          Range.InnerRange innerRange = innerRanges.get(i);
          if (innerRange.getType() != Range.DELETED) continue;

          int start;
          int end;

          if (i == 0) {
            start = lineToY(editor, innerRange.getLine1());
            end = lineToY(editor, innerRange.getLine2()) + 5;
          }
          else if (i == innerRanges.size() - 1) {
            start = lineToY(editor, innerRange.getLine1()) - 5;
            end = lineToY(editor, innerRange.getLine2());
          }
          else {
            start = lineToY(editor, innerRange.getLine1()) - 3;
            end = lineToY(editor, innerRange.getLine2()) + 3;
          }

          g.setColor(getDiffColor(innerRange));
          g.fillRect(x, start, width, end - start);
        }

        g.setColor(gutter.getOutlineColor(false));
        UIUtil.drawLine(g, x, y, endX - 1, y);
        UIUtil.drawLine(g, x, y, x, endY - 1);
        UIUtil.drawLine(g, x, endY - 1, endX - 1, endY - 1);
      }
    }
  }

  private static int lineToY(@NotNull Editor editor, int line) {
    Document document = editor.getDocument();
    if (line >= document.getLineCount()) {
      int y = lineToY(editor, document.getLineCount() - 1);
      return y + editor.getLineHeight() * (line - document.getLineCount() + 1);
    }
    return editor.logicalPositionToXY(editor.offsetToLogicalPosition(document.getLineStartOffset(line))).y;
  }

  public static LineMarkerRenderer createRenderer(final Range range, final LineStatusTracker tracker) {
    return new ActiveGutterRenderer() {
      public void paint(final Editor editor, final Graphics g, final Rectangle r) {
        paintGutterFragment(editor, g, r, range);
      }

      public void doAction(final Editor editor, final MouseEvent e) {
        e.consume();
        final JComponent comp = (JComponent)e.getComponent(); // shall be EditorGutterComponent, cast is safe.
        final JLayeredPane layeredPane = comp.getRootPane().getLayeredPane();
        final Point point = SwingUtilities.convertPoint(comp, ((EditorEx)editor).getGutterComponentEx().getWidth(), e.getY(), layeredPane);
        showActiveHint(range, editor, point, tracker);
      }

      public boolean canDoAction(final MouseEvent e) {
        final EditorGutterComponentEx gutter = (EditorGutterComponentEx)e.getComponent();
        return e.getX() > gutter.getLineMarkerAreaOffset() + gutter.getIconsAreaWidth();
      }
    };
  }

  public static void showActiveHint(final Range range, final Editor editor, final Point point, final LineStatusTracker tracker) {
    final DefaultActionGroup group = new DefaultActionGroup();

    final ShowPrevChangeMarkerAction localShowPrevAction = new ShowPrevChangeMarkerAction(tracker.getPrevRange(range), tracker, editor);
    final ShowNextChangeMarkerAction localShowNextAction = new ShowNextChangeMarkerAction(tracker.getNextRange(range), tracker, editor);
    final RollbackLineStatusRangeAction rollback = new RollbackLineStatusRangeAction(tracker, range, editor);
    final ShowLineStatusRangeDiffAction showDiff = new ShowLineStatusRangeDiffAction(tracker, range, editor);
    final CopyLineStatusRangeAction copyRange = new CopyLineStatusRangeAction(tracker, range);

    group.add(localShowPrevAction);
    group.add(localShowNextAction);
    group.add(rollback);
    group.add(showDiff);
    group.add(copyRange);


    final JComponent editorComponent = editor.getComponent();
    EmptyAction.setupAction(localShowPrevAction, "VcsShowPrevChangeMarker", editorComponent);
    EmptyAction.setupAction(localShowNextAction, "VcsShowNextChangeMarker", editorComponent);
    EmptyAction.setupAction(rollback, IdeActions.SELECTED_CHANGES_ROLLBACK, editorComponent);
    EmptyAction.setupAction(showDiff, "ChangesView.Diff", editorComponent);
    EmptyAction.setupAction(copyRange, IdeActions.ACTION_COPY, editorComponent);


    final JComponent toolbar =
      ActionManager.getInstance().createActionToolbar(ActionPlaces.FILEHISTORY_VIEW_TOOLBAR, group, true).getComponent();

    final Color background = ((EditorEx)editor).getBackgroundColor();
    final Color foreground = editor.getColorsScheme().getColor(EditorColors.CARET_COLOR);
    toolbar.setBackground(background);

    toolbar
      .setBorder(new ColoredSideBorder(foreground, foreground, range.getType() != Range.INSERTED ? null : foreground, foreground, 1));

    final JPanel component = new JPanel(new BorderLayout());
    component.setOpaque(false);

    final JPanel toolbarPanel = new JPanel(new BorderLayout());
    toolbarPanel.setOpaque(false);
    toolbarPanel.add(toolbar, BorderLayout.WEST);
    JPanel emptyPanel = new JPanel();
    emptyPanel.setOpaque(false);
    toolbarPanel.add(emptyPanel, BorderLayout.CENTER);
    MouseAdapter listener = new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        editor.getContentComponent().dispatchEvent(SwingUtilities.convertMouseEvent(e.getComponent(), e, editor.getContentComponent()));
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        editor.getContentComponent().dispatchEvent(SwingUtilities.convertMouseEvent(e.getComponent(), e, editor.getContentComponent()));
      }

      public void mouseReleased(final MouseEvent e) {
        editor.getContentComponent().dispatchEvent(SwingUtilities.convertMouseEvent(e.getComponent(), e, editor.getContentComponent()));
      }
    };
    emptyPanel.addMouseListener(listener);

    component.add(toolbarPanel, BorderLayout.NORTH);


    if (range.getType() != Range.INSERTED) {
      final DocumentEx doc = (DocumentEx)tracker.getVcsDocument();
      final EditorEx uEditor = (EditorEx)EditorFactory.getInstance().createViewer(doc, tracker.getProject());
      final EditorHighlighter highlighter =
        EditorHighlighterFactory.getInstance().createEditorHighlighter(tracker.getProject(), getFileName(tracker.getDocument()));
      uEditor.setHighlighter(highlighter);

      final EditorFragmentComponent editorFragmentComponent =
        EditorFragmentComponent.createEditorFragmentComponent(uEditor, range.getVcsLine1(), range.getVcsLine2(), false, false);

      component.add(editorFragmentComponent, BorderLayout.CENTER);

      EditorFactory.getInstance().releaseEditor(uEditor);
    }


    final List<AnAction> actionList = ActionUtil.getActions(editorComponent);
    final LightweightHint lightweightHint = new LightweightHint(component);
    HintListener closeListener = new HintListener() {
      public void hintHidden(final EventObject event) {
        actionList.remove(rollback);
        actionList.remove(showDiff);
        actionList.remove(copyRange);
        actionList.remove(localShowPrevAction);
        actionList.remove(localShowNextAction);
      }
    };
    lightweightHint.addHintListener(closeListener);

    HintManagerImpl.getInstanceImpl().showEditorHint(lightweightHint, editor, point,
                                                     HintManagerImpl.HIDE_BY_ANY_KEY | HintManagerImpl.HIDE_BY_TEXT_CHANGE |
                                                     HintManagerImpl.HIDE_BY_SCROLLING,
                                                     -1, false, new HintHint(editor, point));

    if (!lightweightHint.isVisible()) {
      closeListener.hintHidden(null);
    }
  }

  private static String getFileName(final Document document) {
    final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file == null) return "";
    return file.getName();
  }

  public static void moveToRange(final Range range, final Editor editor, final LineStatusTracker tracker) {
    final Document document = tracker.getDocument();
    final int lastOffset = document.getLineStartOffset(Math.min(range.getLine2(), document.getLineCount() - 1));
    editor.getCaretModel().moveToOffset(lastOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);

    editor.getScrollingModel().runActionOnScrollingFinished(new Runnable() {
      public void run() {
        Point p = editor.visualPositionToXY(editor.offsetToVisualPosition(lastOffset));
        final JComponent editorComponent = editor.getContentComponent();
        final JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();
        p = SwingUtilities.convertPoint(editorComponent, 0, p.y, layeredPane);
        showActiveHint(range, editor, p, tracker);
      }
    });
  }

  @NotNull
  private static Color getDiffColor(@NotNull Range.InnerRange range) {
    final EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
    switch (range.getType()) {
      case Range.INSERTED:
        return globalScheme.getColor(EditorColors.ADDED_LINES_COLOR);
      case Range.DELETED:
        return globalScheme.getColor(EditorColors.DELETED_LINES_COLOR);
      case Range.MODIFIED:
        return globalScheme.getColor(EditorColors.MODIFIED_LINES_COLOR);
      case Range.EQUAL:
        return globalScheme.getColor(EditorColors.WHITESPACES_MODIFIED_LINES_COLOR);
      default:
        assert false;
        return null;
    }
  }

  @NotNull
  private static Color getDiffColor(@NotNull Range range) {
    final EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
    switch (range.getType()) {
      case Range.INSERTED:
        return globalScheme.getAttributes(DiffColors.DIFF_INSERTED).getErrorStripeColor();
      case Range.DELETED:
        return globalScheme.getAttributes(DiffColors.DIFF_DELETED).getErrorStripeColor();
      case Range.MODIFIED:
        return globalScheme.getAttributes(DiffColors.DIFF_MODIFIED).getErrorStripeColor();
      default:
        assert false;
        return null;
    }
  }

  @NotNull
  private static Color getDiffGutterColor(@NotNull Range range) {
    final EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
    switch (range.getType()) {
      case Range.INSERTED:
        return globalScheme.getColor(EditorColors.ADDED_LINES_COLOR);
      case Range.DELETED:
        return globalScheme.getColor(EditorColors.DELETED_LINES_COLOR);
      case Range.MODIFIED:
        return globalScheme.getColor(EditorColors.MODIFIED_LINES_COLOR);
      default:
        assert false;
        return null;
    }
  }
}
