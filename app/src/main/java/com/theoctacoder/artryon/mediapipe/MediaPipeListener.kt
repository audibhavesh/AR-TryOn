package com.theoctacoder.artryon.mediapipe

import com.google.ar.core.Frame

interface MediaPipeListener {

    fun segmentCurrentFrame(frame: Frame)
}