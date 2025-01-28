/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2021 Pedro Alves
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
package org.dropProject.controllers

import org.dropProject.AsyncConfigurer
import org.dropProject.dao.AssignmentTag
import org.dropProject.dao.SubmissionStatus
import org.dropProject.forms.AdminDashboardForm
import org.dropProject.repository.AssignmentTagRepository
import org.dropProject.repository.AssignmentTagsRepository
import org.dropProject.repository.SubmissionRepository
import org.dropProject.services.MavenInvoker
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.ModelMap
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import javax.transaction.Transactional
import javax.validation.Valid

/**
 * AdminController contains MVC controller functions that handle requests related with DP's administration
 * (for example, the ability to abort a submission that is taking too long, etc.)
 */
@Controller
@RequestMapping("/admin")
class AdminController(val mavenInvoker: MavenInvoker,
                      val submissionRepository: SubmissionRepository,
                      val assignmentTagRepository: AssignmentTagRepository,
                      val assignmentTagsRepository: AssignmentTagsRepository,
                      val asyncConfigurer: AsyncConfigurer) {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    /**
     * Controller to handle HTTP GET requests related with the admin dashboard.
     * @param modelMap is a [ModelMap] that will be populated with the information to use in a View
     * @return A String with the name of the relevant View
     */
    @RequestMapping(value = ["/dashboard"], method = [(RequestMethod.GET)])
    fun showDashboard(model: ModelMap): String {
        model["adminDashboardForm"] = AdminDashboardForm(
            showMavenOutput = mavenInvoker.showMavenOutput,
            asyncTimeout = asyncConfigurer.getTimeout(),
            threadPoolSize = asyncConfigurer.getThreadPoolSize())
        return "admin-dashboard"
    }

    /**
     * Controller to handle HTTP POST requests related with the admin dashboard.
     * @param modelMap is a [ModelMap] that will be populated with the information to use in a View
     * @return A String with the name of the relevant View
     */
    @RequestMapping(value = ["/dashboard"], method = [(RequestMethod.POST)])
    fun postDashboard(@Valid @ModelAttribute("adminDashboardForm") adminDashboardForm: AdminDashboardForm,
                      bindingResult: BindingResult,
                      redirectAttributes: RedirectAttributes): String {

        if (bindingResult.hasErrors()) {
            return "admin-dashboard"
        }

        mavenInvoker.showMavenOutput = adminDashboardForm.showMavenOutput
        asyncConfigurer.setTimeout(adminDashboardForm.asyncTimeout)
        asyncConfigurer.setThreadPoolSize(adminDashboardForm.threadPoolSize)

        redirectAttributes.addFlashAttribute("message", "Operation was successful")
        return "redirect:/admin/dashboard"
    }

    /**
     * Controller to handle requests related with the list of pending assignments.
     * @model is a [ModelMap] that will be populated with the information to use in a View
     * @return A String with the name of the relevant View
     */
    @RequestMapping(value = ["/showPending"], method = [(RequestMethod.GET)])
    fun showPendingSubmissions(model: ModelMap): String {
        val pendingSubmissions = submissionRepository.findByStatusOrderByStatusDate(SubmissionStatus.SUBMITTED.code)
        model["pendingSubmissions"] = pendingSubmissions
        return "admin-pending-submissions"
    }

    /**
     * Controller to handle requests related with aborting a [Submission].
     * @param submissionId is a Long identifying the relevant Submission
     * @param redirectAttributes is a RedirectAttributes
     * @return A String with the name of the relevant View
     */
    @RequestMapping(value = ["/abort/{submissionId}"], method = [(RequestMethod.POST)])
    fun abortSubmission(@PathVariable submissionId: Long,
                        redirectAttributes: RedirectAttributes): String {

        val submission = submissionRepository.findById(submissionId).get()
        submission.setStatus(SubmissionStatus.ABORTED_BY_TIMEOUT)
        submissionRepository.save(submission)

        LOG.info("Aborted submission ${submissionId}")

        redirectAttributes.addFlashAttribute("message", "Aborted submission ${submissionId}")
        return "redirect:/admin/showPending"
    }

    // Method to display all tags and their usage count
    @GetMapping("/tags")
    fun showTags(model: ModelMap): String {
        val tagsWithUsage: List<Pair<AssignmentTag,Long>> = assignmentTagRepository.findAll().map { tag ->
            val usageCount = assignmentTagsRepository.countAssignmentTagsByTagId(tag.id) // Assuming 'assignments' is a list of associated entities
            Pair(tag, usageCount)
        }

        model["tagsWithUsage"] = tagsWithUsage
        return "admin-tags"
    }

    // Method to delete a tag and all its associations
    @PostMapping("/deleteTag")
    @Transactional
    fun deleteTag(@RequestParam("tagId") tagId: Long, redirectAttributes: RedirectAttributes): String {
        val tag = assignmentTagRepository.findById(tagId)
        if (tag.isPresent) {
            assignmentTagsRepository.removeAllByTagId(tag.get().id)
            assignmentTagRepository.delete(tag.get())
            redirectAttributes.addFlashAttribute("message", "Tag deleted successfully.")
        } else {
            redirectAttributes.addFlashAttribute("error", "Tag not found.")
        }
        return "redirect:/admin/tags"
    }
}
