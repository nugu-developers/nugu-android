package com.skt.nugu.sdk.agent.display

/**
 * The basic interface for display
 */
interface DisplayInterface<Renderer, Controller> {
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