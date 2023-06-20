// This function acts on two scenarios, copying the token
// and copying the assigment id that's in the url path
// when targetToCopy is null, it means we're on the assigment id scenario
async function copyToClipboard(buttonContent, targetToCopy=null) {
    let target
    if (targetToCopy==null){
        const path = window.location.pathname; // current path
        const parts = path.split('/');
        target = parts[parts.length - 1]; // last part of the path -> assignment id
    }
    else {
        target = document.getElementById(targetToCopy).textContent;
    }
    const buttonText = document.getElementById(buttonContent);
    const button = buttonText.parentElement
    const buttonTextContent = buttonText.textContent

    try {
        await navigator.clipboard.writeText(target)
        buttonText.textContent = 'Copied!'
        button.classList.add("disabled")
        setTimeout(() => {
            buttonText.textContent = buttonTextContent;
            button.classList.remove("disabled")
        }, 2000)
    } catch (error) {
        console.error('Erro ao copiar', error)
    }
}