package com.toyrobotworkshop.auspex.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for camera dependencies.
 *
 * Camera2Backend and UVCBackend are provided via constructor injection (@Inject),
 * so no explicit @Provides methods are needed here. This module exists as a placeholder
 * for any future camera-related bindings that require custom provisioning.
 */
@Module
@InstallIn(SingletonComponent::class)
object CameraModule
