Source code structure

Drop Project's code itself follows the Maven folder structure. 

The following figure presents the folder structure, as well as the main sub-folders and their description.

![codez](docs/code-folder-structure.png)
    .
    + src
    +-- main
    +---- kotlin
    +------ org.dropProject code related with handling HTTP requests
    +-------- controllers
    +-------- dao
    +-------- data
    +-------- extensions
    +-------- filters
    +-------- repositories
    +-------- security
    +-------- services
    +-- test
    +---- kotlin
    +------ org.dropProject
    
%  .5 \small\textbf{} \scriptsize\textit{code related with handling HTTP requests}.
%  .5 \small\textbf{} \scriptsize\textit{the Data Access Object classes; This is where 
%you will find the Assignment and Submission classes}.
%  .5 \small\textbf{} \scriptsize\textit{auxiliary classes used in session}.
%  .5 \small\textbf{} \scriptsize\textit{code that extends certain classes of the %Java API 
%(e.g. Date)}.
%  .5 \small\textbf{} \scriptsize\textit{classes that intercept and pre-process HTTP %requests}.
%  .5 \small\textbf{} \scriptsize\textit{code that defines functions/interfaces to %find persisted objects}.
%.5 \small\textbf{} \scriptsize\textit{contains access control definitions}.
%  .5 \small\textbf{} \scriptsize\textit{business logic code}.

%  .5 \small\textbf{controllers} \scriptsize\textit{contains tests for controllers}.
%  .5 \small\textbf{dao} \scriptsize\textit{contains tests for Data Access Object classes}.
%  .5 \small\textbf{data} \scriptsize\textit{contains tests for the auxiliary classes}.
%  .5 \small\textbf{...}.
%  .5 \small\textbf{other test folders}.
%  }
