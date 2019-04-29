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
package org.dropProject

import org.dropProject.repository.AssignmentRepository
import org.dropProject.repository.GitSubmissionRepository
import org.dropProject.services.ZipService
import org.json.JSONObject
import org.junit.Assert
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ResourceLoader
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.stereotype.Service
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.io.File
import java.nio.file.Files

@Service
@ActiveProfiles("test")
class TestsHelper {

    @Autowired
    lateinit var zipService: ZipService

    @Autowired
    lateinit var resourceLoader: ResourceLoader

    companion object {
        const val sampleJavaAssignmentPrivateKey = "-----BEGIN RSA PRIVATE KEY-----\n" +
                "MIIEogIBAAKCAQEAlw5XmMJOjiMGylXnfWTKVH2sAHs7KuQHUxnlisWI0EHmB2Bf\n" +
                "hp1xt32ulxhhdTdNPMkGMQjsEH79tDl9+c1DO22tCaOq2VA6olB2/vaeBCOaWm8J\n" +
                "u1z4xu6OKASgYMK9SMoLXqFKE1m2gE8QLi3jzH9x1X6Pk2rFSywnpWGtWfqNKlPl\n" +
                "brEhetf6Ztn/1k3jrRoMFCSVw8IPhJJfhSnPwu8PzLvUg5b4u/lVE3Jieq+QiKKi\n" +
                "aFV4jalxqD3QHpJT4mpAbCHRa/tCsCblGEOeQYloI8OW1LpYOpWkwDPzTEseuCF6\n" +
                "fJ/YVg2iiKnsO7OnuvfoCzN4em/NhZWnLqSVKQIDAQABAoIBAFdG+CHREuZZIpFB\n" +
                "tTDkTWsn+XuFuBf+DKVdLx1RKM17ZdcZPdhfm9azDW9LrPO28i+Ozr8CMrMNTLUX\n" +
                "CsyMZq4tnm8VW5+YFWi3KSoDgCVQFNzvjyXsf+kg6I4Crk959TfbVVplwpEPorzb\n" +
                "8bNc3GPJtxHtwDMi27+lUXrixvBXR53V0LdpTGA8BmxTzlnTi/889/bhi7yWSx87\n" +
                "hinB+a0Zo7/FKoSWVLflUbF99/xIk34GPpeDBVnaUkiK6WrTVVK011Om8o0Krfyb\n" +
                "lj2Io+wH91JSYXg7JqbQbwdwfqHboK12JkOTsDUVnsy1lHdc6cmiJzEzSkS5QjSw\n" +
                "4I8gDaECgYEA5owYoZL5tqBxGw2ryXtS4ap21Uj7xJ+eXE2gCkBd94P3kaRC0Xco\n" +
                "KUzbyvemeEU1pUEo3ksv/uQz1TtbGe3MLVLw4XDPuXl7oielqF7BoXA8pzWKiICO\n" +
                "PCaeMjT6UrlI3QBCtRs8QGc+MxnIdr8x0jS3EISqZhmwWjAX+u7Cg90CgYEAp7ua\n" +
                "j1b6JZKkKQ0KSA6uHPCGoqCjV+hf9AaXABbIOY1M+BkGbg7ttoayRvuolkspJNlf\n" +
                "efNolucbBFg+cN5GbUNtyHHtoODRW1XwFGIBS35tmfXFEjSOHr3vs2Sm4R90Fub5\n" +
                "GVYgRaZufOXvCjqAA7I5pJhMm5stRz25/ymb970CgYAdJSbT/j2dTcker2rBLNr8\n" +
                "dk1Rh0l0wO0HJDUQNrTqXn+EpOxhiJvGJNZAYXBlEfLHMmaVO5IUugqncTqCG6LN\n" +
                "NAgJp/ZKr0Xm6PYzQ89ctlCknsslmILircsf87yViqDgd3D3bjr+tU6SrTa/dEo7\n" +
                "Fbjy2KKmB6dYr23Ipjhm7QKBgBJe/9y3QAqhdw1v+jJOOU++IGDrizhzoR7PIfbG\n" +
                "iAOVsFp0Ezo2tF6Lfjc8FQjxDn6UuFpZCJmOkmz1ZVFjZv9MpVeQ8t/t/8ArN3Jk\n" +
                "EZQ9Mq/sNTt7Oh2v2/MgEQ8TLNndTmcyAbLfObbAUGAkbCT7fkjCzZE1e84Tuq1x\n" +
                "1z1ZAoGAJQMeLuX145SFlRR+XCvFp2hAaK2gI/IU81G9LgJu1EMFp6sBiFtw+4ak\n" +
                "bHocX1drNS/7RIB/BeLxwmwBGM8QkO0EOlrEpNCBvLJsTJb7ES2lU27h2RO36mJW\n" +
                "f0gaNCy09YGQejiDUeWBNcPBxn2eQtDvhFpdJlvroO8LWVn2jmg=\n" +
                "-----END RSA PRIVATE KEY-----"

        const val sampleJavaAssignmentPublicKey = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCXDl" +
                "eYwk6OIwbKVed9ZMpUfawAezsq5AdTGeWKxYjQQeYHYF+GnXG3fa6XGGF1N008yQYxCOwQ" +
                "fv20OX35zUM7ba0Jo6rZUDqiUHb+9p4EI5pabwm7XPjG7o4oBKBgwr1IygteoUoTWbaATx" +
                "AuLePMf3HVfo+TasVLLCelYa1Z+o0qU+VusSF61/pm2f/WTeOtGgwUJJXDwg+Ekl+FKc/C" +
                "7w/Mu9SDlvi7+VUTcmJ6r5CIoqJoVXiNqXGoPdAeklPiakBsIdFr+0KwJuUYQ55BiWgjw5" +
                "bUulg6laTAM/NMSx64IXp8n9hWDaKIqew7s6e69+gLM3h6b82FlacupJUp"

        const val sampleJavaSubmissionPrivateKey = "-----BEGIN RSA PRIVATE KEY-----\n" +
                "MIIEogIBAAKCAQEAhTnnSFZt4B+Egwm3RNBDOyXkgJVzUmQK/s5HRIdRwEX4N/+F\n" +
                "EfsPoPQJLCEsuRgSTIgk8oMqSTR5qhfDKTzuyvnD3RzrqUfh065vKwnbHeYySEqB\n" +
                "nMPS7ucNq6+rWWrZK4rpXFgekb0tcf11xhZiHPbKvPCFOGrAzR3uTtR4w4YrG548\n" +
                "31E2XoVOXb8yiTVzNTubG03/HGNsQ1V03/er/vhGXFwLIPcCnXvDGRmZCOzVPE4n\n" +
                "bJQsk9EuLiIflz9VkdoxmoSTatpP4J9mHh2TfU/dmW1S4tsZ1ur5UBZ+JdSuf3k2\n" +
                "36OUSPYqpaLjsjB7xDXZVT4adfvQAyC/J2eebQIDAQABAoIBABFHdUvYid+rttAY\n" +
                "DoEKUe31+DEFMTPabeKmdm/Y7S125b69kVDHszs464ehtd871APBFKmvgWiFOdJp\n" +
                "ypIqqP4Cbvjaq2YFQLDnyttWJ+k01giyQSVH8K+zn/5IhnzOcuRG3AVyGGTmmGJU\n" +
                "cVYDyS3ghGME/wJWNjNkUmg+/nq9WBOlRl3LR6UZLFSxP3zQ4K1SsOVSZMUZzQRm\n" +
                "qqB/VmnDBZc/J9uRyzeAnEfpRI+Uv7pqq13GT7gqPw1IvgQxC8Nk9xa3yR8d5T5E\n" +
                "l0UnfmtEA8/zjFegE8mcsdy4SBAluTXBs3LAF5O0cfoYhJ3E5OtRIenVVZJztQGn\n" +
                "Ui5ckbECgYEAxDnBTFvpwXJmOPX5vITlTa6GQkajiCZsILrBwg2wWaO0kaOvy5Cf\n" +
                "gF9CvsY7BhgLLxzp+wygz8AJ296AXcWizQX6mzC4zDgKHnU1nK+NNC6tayQqySzB\n" +
                "KS8YOs7eS/Q4UKZLFM7xfEpI7mBX0S8pvrmoqVX2DIaQTZ8lzvTCHK8CgYEArc9F\n" +
                "FgH4GTubfJery5v5e7eEnCxjzlE0jQDQ7UZaY4z5wanz9IyOuIlH9jaDH2JTRgMh\n" +
                "BHxUxz+pOjsodtujhaROli6O8f+kkmS7a2mKmkEwY6QkaVRjFVkkMrdd4TmCECkV\n" +
                "wwAVpx64oSYEJ2eiTjOwz0pbd9XXd6CwArMbFaMCgYBn0W7V6aNJeC9hX7Lb7Sws\n" +
                "53OdSSZoeHuo7WZqNzfglV6J16LA/ymEj+IOcW71EG+KY6/f5ZSHlkEhFR2xf0ld\n" +
                "VBZ9WU/MrLGd38GXvsHko/WYxC/m9EjWc1ZMdvriELpi4TjEL6FQczUW+d48jMl3\n" +
                "YV89CH//rIpYpyUn1BOHfQKBgDr1X1MK0fUFQkYuUOldfHCaHRK5ABYhd7kI+NY0\n" +
                "Ej9IuAQYuZdQAq3Ya5+6eBoySVsrfoy9/CgmkSoenShMcxjHVp0dKAIxHMtH/kd+\n" +
                "YrTWvipeqLdOF9pLBbtqdh8LWfJPbYFbSv0Ir8qCUdBoGCd841I9v+9Ti6aZzHrt\n" +
                "9JcPAoGAaOJ3M/2rVI2+w/UzxesSEpQvxdIAxqAlF4ZhauGOcDgadhJJ8pxAax6P\n" +
                "IjF9wfEoaGjas/xK1f6Di43vnj+99ZUK8QZ+QXL4tLNswu78/RzSrscAd+7hHwJU\n" +
                "8QTigXeXSxu7XahmOTkC8vCJaaEuPnrdrAcbtqbCGMw5+SMz41k=\n" +
                "-----END RSA PRIVATE KEY-----"

        const val sampleJavaSubmissionPublicKey = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCFOedIVm3" +
                "gH4SDCbdE0EM7JeSAlXNSZAr+zkdEh1HARfg3/4UR+w+g9AksISy5GBJMiCTygypJNHmqF8MpPO" +
                "7K+cPdHOupR+HTrm8rCdsd5jJISoGcw9Lu5w2rr6tZatkriulcWB6RvS1x/XXGFmIc9sq88IU4a" +
                "sDNHe5O1HjDhisbnjzfUTZehU5dvzKJNXM1O5sbTf8cY2xDVXTf96v++EZcXAsg9wKde8MZGZkI" +
                "7NU8TidslCyT0S4uIh+XP1WR2jGahJNq2k/gn2YeHZN9T92ZbVLi2xnW6vlQFn4l1K5/eTbfo5R" +
                "I9iqlouOyMHvENdlVPhp1+9ADIL8nZ55t"
    }



    fun createAndSetupAssignment(mvc: MockMvc,
                                 assignmentRepository: AssignmentRepository,
                                 assignmentId: String, assignmentName: String,
                                 assignmentPackage: String, submissionMethod: String,
                                 repositoryUrl: String, privateKey: String = sampleJavaAssignmentPrivateKey,
                                 publicKey: String = sampleJavaAssignmentPublicKey,
                                 assignees: String? = null, acl: String? = null,
                                 teacherId: String = "teacher1", language: String = "JAVA",
                                 activateRightAfterCloning: Boolean = false) {

        val user = User(teacherId, "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))

        // post form
        mvc.perform(MockMvcRequestBuilders.post("/assignment/new")
                .with(SecurityMockMvcRequestPostProcessors.user(user))
                .param("assignmentId", assignmentId)
                .param("assignmentName", assignmentName)
                .param("assignmentPackage", assignmentPackage)
                .param("language", language)
                .param("submissionMethod", submissionMethod)
                .param("gitRepositoryUrl", repositoryUrl)
                .param("assignees", assignees)
                .param("acl", acl)
        )
                .andExpect(MockMvcResultMatchers.status().isFound())
                .andExpect(MockMvcResultMatchers.header().string("Location", "/assignment/setup-git/${assignmentId}"))

        // get assignment detail
        mvc.perform(MockMvcRequestBuilders.get("/assignment/setup-git/${assignmentId}")
                .with(SecurityMockMvcRequestPostProcessors.user(user)))
                .andExpect(MockMvcResultMatchers.status().isOk)

        // inject private and public key to continue
        var assignment = assignmentRepository.getOne(assignmentId)
        assignment.gitRepositoryPrivKey = privateKey
        assignment.gitRepositoryPubKey = publicKey
        if (activateRightAfterCloning) {
            assignment.active = true
        }
        assignmentRepository.save(assignment)

        // connect to git repository
        mvc.perform(MockMvcRequestBuilders.post("/assignment/setup-git/${assignmentId}")
                .with(SecurityMockMvcRequestPostProcessors.user(user)))
                .andExpect(MockMvcResultMatchers.status().isFound())
                .andExpect(MockMvcResultMatchers.header().string("Location", "/assignment/info/${assignmentId}"))
                .andExpect(MockMvcResultMatchers.flash().attribute<String>("message", "Assignment was successfully created and connected to git repository"))
    }

    fun connectToGitRepositoryAndBuildReport(mvc: MockMvc, gitSubmissionRepository: GitSubmissionRepository,
                                             assignmentId: String, gitRepository: String,
                                             studentUsername: String,
                                             privateKey : String = TestsHelper.sampleJavaSubmissionPrivateKey,
                                             publicKey : String = TestsHelper.sampleJavaSubmissionPublicKey) : Long {

        val student = User(studentUsername, "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))

        /*** POST /student/setup-git ***/
        mvc.perform(MockMvcRequestBuilders.post("/student/setup-git")
                .param("assignmentId", assignmentId)
                .param("gitRepositoryUrl", gitRepository)
                .with(user(student))
        )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.view().name("student-setup-git"))


        val id = gitSubmissionRepository.findAll().last().id

        val gitSubmission = gitSubmissionRepository.getOne(id)
        Assert.assertFalse(gitSubmission.connected)

        // inject public and private key
        gitSubmission.gitRepositoryPrivKey = privateKey
        gitSubmission.gitRepositoryPubKey = publicKey
        gitSubmissionRepository.save(gitSubmission)

        /*** POST /student/setup-git-2 ***/
        mvc.perform(MockMvcRequestBuilders.post("/student/setup-git-2/${id}")
                .with(user(student)))
                .andExpect(MockMvcResultMatchers.status().isFound())
                .andExpect(MockMvcResultMatchers.header().string("Location", "/upload/${assignmentId}"))
                .andExpect(MockMvcResultMatchers.flash().attribute<String>("message", "Ligado com sucesso ao repositório git"))

        val updatedGitSubmission = gitSubmissionRepository.getOne(id)
        Assert.assertTrue(updatedGitSubmission.connected)

        /*** GET /upload/ ***/
        mvc.perform(MockMvcRequestBuilders.get("/upload/${assignmentId}"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.view().name("student-git-form"))
                .andExpect(MockMvcResultMatchers.model().attribute("gitSubmission", updatedGitSubmission))

        /*** POST /git-submission/generate-report ***/
        mvc.perform(MockMvcRequestBuilders.post("/git-submission/generate-report/${id}")
                .with(user(student)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().json("{ \"submissionId\": \"1\"}"))

        return gitSubmission.id
    }

    // returns the submission id
    fun uploadProject(mvc: MockMvc, projectName: String, assignmentId: String, uploader: User,
                      authors: List<Pair<String,String>>? = null): Int {

        val projectFolder = resourceLoader.getResource("file:src/test/sampleProjects/$projectName").file
        val authorsFile = File(projectFolder, "AUTHORS.txt")
        val authorsBackupFile = File(projectFolder, "AUTHORS.txt.bak")
        if (authors != null) {
            // backup original AUTHORS.txt
            authorsFile.copyTo(authorsBackupFile)

            // create another AUTHORS.txt populated with the contents of parameter 'authors'
            val writer = Files.newBufferedWriter(authorsFile.toPath())
            for ((authorId,authorName) in authors) {
                writer.write("$authorId;$authorName")
                writer.newLine()
            }
            writer.close()
        }


        val zipFile = zipService.createZipFromFolder("test", projectFolder)
        zipFile.file.deleteOnExit()

        if (authors != null) {
            // restore original AUTHORS.txt
            authorsBackupFile.copyTo(authorsFile, overwrite = true)
            authorsBackupFile.delete()
        }

        val multipartFile = MockMultipartFile("file", zipFile.file.name, "application/zip", zipFile.file.readBytes())

        val contentString = mvc.perform(MockMvcRequestBuilders.fileUpload("/upload")
                .file(multipartFile)
                .param("assignmentId", assignmentId)
                .param("async", "false")
                .with(SecurityMockMvcRequestPostProcessors.user(uploader)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn().response.contentAsString

        val contentJSON = JSONObject(contentString)

        return contentJSON.getInt("submissionId")
    }
}
