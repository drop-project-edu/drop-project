$( document ).ready(function() {
    var gResponse;

    if (typeof Dropzone !== 'undefined') {

        Dropzone.options.myAwesomeDropzone = {
            paramName: "file", // The name that will be used to transfer the file
            maxFilesize: 1, // MB
            acceptedFiles: ".zip",
            parallelUploads: 1,
            dictDefaultMessage: "Seleccione ou arraste para aqui o ficheiro .zip com o projecto",
            dictInvalidFileType: "Tipo de ficheiro inválido: tem que ser um zip",
            dictFileTooBig: "Ficheiro demasiado grande",
            init: function () {
                this.on("success", function (file) {
                    gResponse = $.trim(file.xhr.response);
                    var jsonResponse = JSON.parse($.trim(file.xhr.response));
                    window.location.replace(context + "buildReport/" + jsonResponse.submissionId);
                });
                this.on('error', function (file, errorMessage, xhr) {
                    if (errorMessage !== null && typeof errorMessage === 'string' &&
                        (errorMessage.indexOf('401') !== -1 || errorMessage.indexOf('403') !== -1)) {  // lost session
                        location.reload(true);
                    } else if (errorMessage !== null && errorMessage.error !== undefined) {
                        // alert(errorMessage.error);
                    }

                });
                this.on("addedfile", function () {
                    if (this.files[1] != null) {
                        this.removeFile(this.files[0]);
                    }
                });
            }
        };
    };
});
