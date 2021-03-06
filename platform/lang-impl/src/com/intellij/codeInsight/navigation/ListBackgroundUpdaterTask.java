/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.navigation;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.ui.components.JBList;
import com.intellij.ui.speedSearch.NameFilteringListModel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User: anna
 */
public abstract class ListBackgroundUpdaterTask extends BackgroundUpdaterTask<JBList> {
  public ListBackgroundUpdaterTask(@Nullable final Project project, @NotNull final String title) {
    super(project, title);
  }

  @Override
  protected void paintBusy(final boolean paintBusy) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        myComponent.setPaintBusy(paintBusy);
      }
    });
  }

  @Override
  protected void replaceModel(@NotNull List<PsiElement> data) {
    final Object selectedValue = myComponent.getSelectedValue();
    final int index = myComponent.getSelectedIndex();
    ((NameFilteringListModel)myComponent.getModel()).replaceAll(data);
    if (index == 0) {
      myComponent.setSelectedIndex(0);
    }
    else {
      myComponent.setSelectedValue(selectedValue, true);
    }
  }
}
