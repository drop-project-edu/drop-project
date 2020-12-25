package org.dropProject.controllers

import org.dropProject.forms.AdminDashboardForm
import org.dropProject.services.MavenInvoker
import org.springframework.stereotype.Controller
import org.springframework.ui.ModelMap
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import javax.validation.Valid

@Controller
@RequestMapping("/admin")
class AdminController(val mavenInvoker: MavenInvoker) {

    @RequestMapping(value = ["/dashboard"], method = [(RequestMethod.GET)])
    fun showDashboard(model: ModelMap): String {
        model["adminDashboardForm"] = AdminDashboardForm(showMavenOutput = mavenInvoker.showMavenOutput)
        return "admin-dashboard"
    }

    @RequestMapping(value = ["/dashboard"], method = [(RequestMethod.POST)])
    fun postDashboard(@Valid @ModelAttribute("adminDashboardForm") adminDashboardForm: AdminDashboardForm,
                      bindingResult: BindingResult,
                      redirectAttributes: RedirectAttributes): String {

        if (bindingResult.hasErrors()) {
            return "admin-dashboard"
        }

        mavenInvoker.showMavenOutput = adminDashboardForm.showMavenOutput

        redirectAttributes.addFlashAttribute("message", "Operation was successful")
        return "redirect:/admin/dashboard"
    }
}