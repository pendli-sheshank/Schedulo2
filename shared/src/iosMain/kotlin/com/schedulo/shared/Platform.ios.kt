package com.schedulo.shared

import platform.UIKit.UIDevice

actual fun platformName(): String = "iOS ${UIDevice.currentDevice.systemVersion}"
