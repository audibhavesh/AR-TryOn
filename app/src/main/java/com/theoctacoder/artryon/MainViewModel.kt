package com.theoctacoder.artryon

import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {

    private var _delegate: Int = ImageSegmenterHelper.DELEGATE_GPU
    private var _model: Int = ImageSegmenterHelper.MODEL_SELFIE_MULTICLASS

    val currentDelegate: Int get() = _delegate
    val currentModel: Int get() = _model

    fun setDelegate(delegate: Int) {
        _delegate = delegate
    }

    fun setModel(model: Int) {
        _model = model
    }
}
