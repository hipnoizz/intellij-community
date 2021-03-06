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
package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.components.*;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.SmartList;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author nik
 * todo rename to XDebuggerSettingsManagerImpl
 */
@SuppressWarnings("ClassNameSameAsAncestorName")
@State(
  name = "XDebuggerSettings",
  storages = {
    @Storage(file = StoragePathMacros.APP_CONFIG + "/other.xml"),
    @Storage(file = StoragePathMacros.APP_CONFIG + "/debugger.xml")
  },
  storageChooser = LastStorageChooserForWrite.class
)
public class XDebuggerSettingsManager extends com.intellij.xdebugger.settings.XDebuggerSettingsManager implements PersistentStateComponent<XDebuggerSettingsManager.SettingsState> {
  private Map<String, XDebuggerSettings<?>> mySettingsById;
  private Map<Class<? extends XDebuggerSettings>, XDebuggerSettings<?>> mySettingsByClass;
  private XDebuggerDataViewSettings myDataViewSettings = new XDebuggerDataViewSettings();
  private XDebuggerGeneralSettings myGeneralSettings = new XDebuggerGeneralSettings();

  public static XDebuggerSettingsManager getInstanceImpl() {
    return (XDebuggerSettingsManager)com.intellij.xdebugger.settings.XDebuggerSettingsManager.getInstance();
  }

  @Override
  public SettingsState getState() {
    SettingsState settingsState = new SettingsState();
    settingsState.setDataViewSettings(myDataViewSettings);
    settingsState.setGeneralSettings(myGeneralSettings);

    initSettings();
    if (!mySettingsById.isEmpty()) {
      SkipDefaultValuesSerializationFilters filter = new SkipDefaultValuesSerializationFilters();
      for (XDebuggerSettings<?> settings : mySettingsById.values()) {
        Object subState = settings.getState();
        if (subState != null) {
          Element serializedState = XmlSerializer.serializeIfNotDefault(subState, filter);
          if (!JDOMUtil.isEmpty(serializedState)) {
            SpecificSettingsState state = new SpecificSettingsState();
            state.id = settings.getId();
            state.configuration = serializedState;
            settingsState.specificStates.add(state);
          }
        }
      }
    }
    return settingsState;
  }

  public Collection<XDebuggerSettings<?>> getSettingsList() {
    initSettings();
    return Collections.unmodifiableCollection(mySettingsById.values());
  }

  @Override
  @NotNull
  public XDebuggerDataViewSettings getDataViewSettings() {
    return myDataViewSettings;
  }

  public XDebuggerGeneralSettings getGeneralSettings() {
    return myGeneralSettings;
  }

  @Override
  public void loadState(final SettingsState state) {
    myDataViewSettings = state.getDataViewSettings();
    myGeneralSettings = state.getGeneralSettings();
    for (SpecificSettingsState settingsState : state.specificStates) {
      XDebuggerSettings<?> settings = findSettings(settingsState.id);
      if (settings != null) {
        ComponentSerializationUtil.loadComponentState(settings, settingsState.configuration);
      }
    }
  }

  private XDebuggerSettings findSettings(String id) {
    initSettings();
    return mySettingsById.get(id);
  }

  private void initSettings() {
    if (mySettingsById == null) {
      XDebuggerSettings[] extensions = XDebuggerSettings.EXTENSION_POINT.getExtensions();
      mySettingsById = new TreeMap<String, XDebuggerSettings<?>>();
      mySettingsByClass = new THashMap<Class<? extends XDebuggerSettings>, XDebuggerSettings<?>>(extensions.length);
      for (XDebuggerSettings settings : extensions) {
        mySettingsById.put(settings.getId(), settings);
        mySettingsByClass.put(settings.getClass(), settings);
      }
    }
  }

  public <T extends XDebuggerSettings<?>> T getSettings(final Class<T> aClass) {
    initSettings();
    //noinspection unchecked
    return (T)mySettingsByClass.get(aClass);
  }

  public static class SettingsState {
    @Tag("debuggers")
    @AbstractCollection(surroundWithTag = false)
    public List<SpecificSettingsState> specificStates = new SmartList<SpecificSettingsState>();
    private XDebuggerDataViewSettings myDataViewSettings = new XDebuggerDataViewSettings();
    private XDebuggerGeneralSettings myGeneralSettings = new XDebuggerGeneralSettings();

    @Property(surroundWithTag = false)
    public XDebuggerDataViewSettings getDataViewSettings() {
      return myDataViewSettings;
    }

    public void setDataViewSettings(XDebuggerDataViewSettings dataViewSettings) {
      myDataViewSettings = dataViewSettings;
    }

    @Property(surroundWithTag = false)
    public XDebuggerGeneralSettings getGeneralSettings() {
      return myGeneralSettings;
    }

    public void setGeneralSettings(XDebuggerGeneralSettings generalSettings) {
      myGeneralSettings = generalSettings;
    }
  }

  @Tag("debugger")
  static class SpecificSettingsState {
    @Attribute
    public String id;
    @Tag
    public Element configuration;
  }
}
