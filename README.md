<h1 align="center">Montoya Kotlin Utilities</h1>
<h4 align="center">Kotlin-based Preference and GUI Library for Burp Suite</h4>
<h4 align="center" markdown="1">by Corey Arthur</h4>
<p align="center">

  <img src="https://img.shields.io/github/watchers/coreyd97/montoya-kotlin-utilities?label=Watchers&style=for-the-badge" alt="GitHub Watchers">
  <img src="https://img.shields.io/github/stars/coreyd97/montoya-kotlin-utilities?style=for-the-badge" alt="GitHub Stars">
  <img src="https://img.shields.io/github/downloads/coreyd97/montoya-kotlin-utilities/total?style=for-the-badge" alt="GitHub All Releases">
  <img src="https://img.shields.io/github/license/coreyd97/montoya-kotlin-utilities?style=for-the-badge" alt="GitHub License">
</p>

---
# Overview

----------

This library aims to simplify the process of creating user interfaces and persisting data/preferences for Kotlin-based Burp Suite extensions. It consists of two main parts:
- [Preference Management](#preferences),
- [User Interface Generation](#user-interfaces).

# Installing

------------

To use the library, simply add the following to your `build.gradle` file (Gradle) or `pom.xml` (Maven), where `[VERSION]` is a commit hash, version tag or `latest`.

### Gradle

```groovy
repositories {
    maven {
        url "https://jitpack.io"
    }
}

dependencies {
    implementation 'com.github.CoreyD97:montoya-kotlin-utilities:[VERSION]'
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.CoreyD97</groupId>
    <artifactId>montoya-kotlin-utilities</artifactId>
    <version>[VERSION]</version>
</dependency>
```

# Preferences

-------------

This library can be used to define preferences to be used by your extension. The library will take care of handling
default values and serializing/deserializing the values for storing in Burp so that even complex objects can be stored.

The Montoya API has improved support for different data types, but this is still limited to more generic types provided 
by the API such as ByteArray, and HTTP requests and responses. If you want to store anything more complex, it needs to be
serialized and stored as a string in the Burp project.

## Registering Preferences
The recommended implementation is by creating a class that extends `PreferenceFactory`, but preferences can also be registered on the fly within the [`Preferences` object](#Preferences-Object). 
Both work the same way, but a `PreferenceFactory` can help structure the creation process.

[//]: # (TODO)

# User Interfaces

-----------------

The library also provides functions to simplify the process of creating user interfaces, and can automatically integrate
controls for your extension's preferences defined within the library.

[//]: # (TODO)

# Utilities

-----------

This library also ships with a number of utility classes that proved helpful in building responsive extensions. These are listed below.

## Variable View Panel
A panel that can switch between horizontal, vertical or tabbed layout as required. 
The layout can also be loaded from a preference so that it is restored when the extension is loaded, and will automatically register the preference for you.

```java
JLabel leftPanel = new JLabel("Some Content");
JLabel rightPanel = new JLabel("Different Content");
VariableViewPanel variablePanel = new VariableViewPanel(preferences, "myVariablePanel", leftPanel, 
        "Left", rightPanel, "Right", VariableViewPanel.View.HORIZONTAL);

//Change to Vertical layout
variablePanel.setView(VariableViewPanel.View.VERTICAL);

//Change to Horizontal layout
variablePanel.setView(VariableViewPanel.View.HORIZONTAL);

//Change to Tabbed layout
variablePanel.setView(VariableViewPanel.View.TABS);
```

## Pop Out Panel
A simple panel wrapper that allows the component to be popped out into a separate frame. 
By default, the library will display a placeholder indicating that the component is popped out, but this can be disabled as shown below.

```java
JPanel contentPanel = new JPanel();
PopOutPanel popout = new PopOutPanel(montoya, contentPanel, "Example");
//To hide the placeholder, call the constructor with showPlaceholder = false
//PopOutPanel popout = new PopOutPanel(montoya, contentPanel, "Example", false);

popout.popOut(); //Pop Out
popout.popIn(); //Pop In
popout.toggle(); //Toggle State

//Simple menu item to pop your component in/out.
//Can be added to the element's context menu, or Burp's own JMenu.
JMenuItem menuItem = popout.getPopoutMenuItem(); 
```

![img.png](popout.png)
