/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 Pedro Alves
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.dropProject.services

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.AttributeProvider
import org.commonmark.renderer.html.HtmlRenderer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

/**
 * Transforms relative links into absolute links, during the rendering of markdown documents
 */
class RelativeToAbsoluteLinkVisitor(private val baseUrlForLinks: String,
                                    private val baseUrlForImages: String) : AbstractVisitor() {

    override fun visit(image: Image) {
        val destination = image.destination

        // Check if the link is relative
        if (!destination.startsWith("http://") && !destination.startsWith("https://")) {
            // Convert the relative link to an absolute one
            image.destination = baseUrlForImages + destination
        }

        super.visit(image)
    }

    override fun visit(link: Link) {
        val destination = link.destination

        // Check if the link is relative
        if (!destination.startsWith("http://") && !destination.startsWith("https://")) {
            // Convert the relative link to an absolute one
            link.destination = baseUrlForLinks + destination
        }

        // Proceed with the default behavior for this node
        visitChildren(link)
    }
}

/**
 * Utility to perform the rendering of markdown files to html.
 */
@Service
class MarkdownRenderer {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    /**
     * Transforms markdown content (using github markdown flavor) to html,
     * replacing links and images with absolute paths
     */
    fun render(markdownContent: String, baseUrlForLinks: String, baseUrlForImages: String) : String {

        val extensions = listOf(AutolinkExtension.create(), TablesExtension.create())
        val parser = Parser.builder().extensions(extensions).build();
        val document = parser.parse(markdownContent);

        // Create the visitor with the base URL for converting relative links
        val visitor = RelativeToAbsoluteLinkVisitor(baseUrlForLinks, baseUrlForImages)

        // Apply the visitor to the document
        document.accept(visitor)

        val renderer = HtmlRenderer.builder()
            .extensions(extensions)
            // custom attribute provider to add 'table' class to tables rendered by commonmark
            .attributeProviderFactory { _ ->
                AttributeProvider { node, tagName, attributes ->
                    if (node is TableBlock) {
                        attributes["class"] = "table table-bordered"
                    }
                }
            }
            .build();
        return renderer.render(document)
    }
}
