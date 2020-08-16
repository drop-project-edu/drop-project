Este projecto corre testes dependendo do número de aluno.

Para se conseguir testar no Intellij:

- Caso se corra a partir do "Run Test", adicionar às VM Options: -DdropProject.currentUserId=p4997
- Caso se corra a partir do Maven, adicionar ao Runner>VM Options: -Ddp.argLine="-DdropProject.currentUserId=p4997"