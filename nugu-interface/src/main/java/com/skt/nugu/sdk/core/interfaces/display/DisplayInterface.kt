package com.skt.nugu.sdk.core.interfaces.display

/**
 * The basic interface for display
 */
interface DisplayInterface<Renderer> {
    /**
     * Each element has it's own token.
     *
     * This should be called when element selected(clicked) by the renderer.
     *
     * @param templateId the unique identifier for the template card
     * @param token the unique identifier for the element
     */
    fun setElementSelected(templateId: String, token: String)

    /**
     * Notifies the display that has been rendered.
     *
     * This should be called when the display rendered by the renderer.
     *
     * @param templateId the templateId that has been rendered
     */
    fun displayCardRendered(templateId: String)

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

    /**
     * Stop rendering timer for [templateId].
     * The SDK manage a timer to notify proper display clear timing.
     * Call this API to stop timer, if client do not want the behavior.
     * @param templateId the templateId associated with internal timer
     */
    fun stopRenderingTimer(templateId: String)
}