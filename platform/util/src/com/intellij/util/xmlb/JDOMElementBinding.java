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
package com.intellij.util.xmlb;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

class JDOMElementBinding implements Binding {
  private final Accessor myAccessor;
  private final String myTagName;

  public JDOMElementBinding(@NotNull Accessor accessor) {
    myAccessor = accessor;
    Tag tag = myAccessor.getAnnotation(Tag.class);
    assert tag != null : "jdom.Element property without @Tag annotation: " + accessor;

    String tagName = tag.value();
    if (StringUtil.isEmpty(tagName)) {
      tagName = myAccessor.getName();
    }
    myTagName = tagName;
  }

  @Override
  public Object serialize(Object o, Object context, SerializationFilter filter) {
    Object value = myAccessor.read(o);
    if (value == null) {
      return null;
    }

    if (value instanceof Element) {
      Element targetElement = ((Element)value).clone();
      assert targetElement != null;
      targetElement.setName(myTagName);
      return targetElement;
    }
    if (value instanceof Element[]) {
      ArrayList<Element> result = new ArrayList<Element>();
      for (Element element : ((Element[])value)) {
        result.add(element.clone().setName(myTagName));
      }
      return result;
    }
    throw new XmlSerializationException("org.jdom.Element expected but " + value + " found");
  }

  @Override
  @Nullable
  public Object deserialize(Object context, @NotNull Object... nodes) {
    if (myAccessor.getValueClass().isArray()) {
      Element[] result = new Element[nodes.length];
      System.arraycopy(nodes, 0, result, 0, nodes.length);
      myAccessor.write(context, result);
    }
    else {
      assert nodes.length == 1;
      myAccessor.write(context, nodes[0]);
    }
    return context;
  }

  @Override
  public boolean isBoundTo(Object node) {
    return node instanceof Element && ((Element)node).getName().equals(myTagName);
  }

  @Override
  public Class getBoundNodeType() {
    throw new UnsupportedOperationException("Method getBoundNodeType is not supported in " + getClass());
  }

  @Override
  public void init() {
  }
}
