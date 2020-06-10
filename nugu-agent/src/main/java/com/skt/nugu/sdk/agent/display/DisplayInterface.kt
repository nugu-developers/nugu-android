package com.skt.nugu.sdk.agent.display

import com.skt.nugu.sdk.core.interfaces.common.EventCallback

/**
 * The basic interface for display
 */
interface DisplayInterface<Renderer, Controller> {
    /**
     * enum class for ErrorType
     */
    enum class ErrorType {
        REQUEST_FAIL,
        RESPONSE_TIMEOUT
    }

    /**
     * callback interface for [setElementSelected]
     */
    interface OnElementSelectedCallback : EventCallback<ErrorType>

    /**
     * Each element has it's own token.
     *
     * This should be called when element selected(clicked) by the renderer.
     *
     * @param templateId the unique identifier for the template card
     * @param token the unique identifier for the element
     * @param postback the data in structured json object which associated with [token]. Can be null if not exist.
     * @param callback the result callback for element selected event
     * @throws IllegalStateException when received invalid call.
     * for example, when display for given [templateId] is invalid (maybe cleared or not rendered)
     * @return the dialogRequestId for request
     */
    fun setElementSelected(templateId: String, token: String, postback: String? = null, callback: OnElementSelectedCallback? = null): String

    /**
     * Notifies the display that has been rendered.
     *
     * This should be called when the display rendered by the renderer.
     *
     * @param templateId the templateId that has been rendered
     * @param controller the controller for template identified by [templateId]
     */
    fun displayCardRendered(templateId: String, controller: Controller?)

    /**
     * Notifies the display that has been render failed.
     *
     * This should be called when the display rendering failed by the renderer.
     *
     * @param templateId the templateId that has been render failed.
     */
    fun displayCardRenderFailed(templateId: String)

    /**
     * Notifies the display that has been cleared.
     *
     * This should be called when the display cleared by the renderer.
     *
     * @param templateId the templateId that has been cleared
     */
    fun displayCardCleared(templateId: String)

    /** Set a renderer to interact with the display agent.
     * @param renderer the renderer to be set
     */
    fun setRenderer(renderer: Renderer?)
}