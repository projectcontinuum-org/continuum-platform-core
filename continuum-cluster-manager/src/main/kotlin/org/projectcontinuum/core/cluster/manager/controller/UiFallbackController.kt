package org.projectcontinuum.core.cluster.manager.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

// SPA fallback controller for the management UI.
// Forwards any /ui/ sub-route that doesn't match a static file to index.html
// so that React Router can handle client-side routing.
@Controller
class UiFallbackController {

  @GetMapping("/ui", "/ui/", "/ui/{path:[^\\.]*}", "/ui/{path:[^\\.]*}/{subpath:[^\\.]*}")
  fun forwardToUi(): String {
    return "forward:/ui/index.html"
  }
}

