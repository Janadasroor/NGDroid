package com.jnd.ngdroid.ui.assistant

import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.model.ImageTransformer

/**
 * Coil 3-backed image loader for assistant markdown (`![alt](https://…)`),
 * so responses can show circuit diagrams, plots and photos inline.
 * Uses the official mikepenz coil3 transformer.
 */
val AssistantImageTransformer: ImageTransformer = Coil3ImageTransformerImpl
