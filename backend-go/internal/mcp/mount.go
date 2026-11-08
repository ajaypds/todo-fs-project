// Package mcp implements the built-in Model Context Protocol server.
package mcp

import (
	"net/http"

	"github.com/gin-gonic/gin"
)

// Mount registers the MCP routes. (Placeholder until the full implementation lands.)
func Mount(r *gin.Engine, api func() http.Handler) {}
