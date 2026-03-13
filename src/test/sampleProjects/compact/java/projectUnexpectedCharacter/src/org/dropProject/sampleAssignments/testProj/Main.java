package org.dropProject.sampleAssignments.testProj;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;
import java.io.File;
import java.io.FileNotFoundException;

class Filme
{
    int id;
    String titulo;
    DataLancamento data;
    int orcamento;
    double duracao;
    double mediaDeVotos;
    int nrVotos;
    ArrayList<Actor> atores;
    ArrayList<GeneroCinematografico> generos;

    public Filme(int id, String titulo, DataLancamento data, int orcamento, double duracao, double mediaDeVotos, int nrVotos)
    {
        this.id = id;
        this.titulo = titulo;
        this.data = data;
        this.orcamento = orcamento;
        this.duracao = duracao;
        this.mediaDeVotos = mediaDeVotos;
        this.nrVotos = nrVotos;
        this.atores = new ArrayList<Actor>();
        this.generos = new ArrayList<GeneroCinematografico>();
}
    public Filme()
    {}
    public String toString()
    {
        return id +" | "+ titulo +" | "+ data+" | "+atores.size()+" | "+generos.size();
    }
}
class DataLancamento
{
    int dia;
    int mes;
    int ano;
    public DataLancamento(int dia, int mes, int ano)
    {
        this.dia = dia;
        this.mes = mes;
        this.ano = ano;
    }
    public DataLancamento()
    {}
    public String toString()
    {
        if(mes < 10)
        {
            return dia+"-0"+mes+"-"+ano;
        }
        else
        {
            return dia+"-"+mes+"-"+ano;
        }
    }
}
class Actor
{
    int idActor;
    String nome;
    boolean genero;

    public Actor(int idActor,String nome, boolean genero)
    {
        this.idActor = idActor;
        this.nome = nome;
        this.genero = genero;
    }
    public  Actor()
    {}
}
class GeneroCinematografico
{
    String nome;

    public GeneroCinematografico(String nome)
    {
        this.nome = nome;
    }
    public GeneroCinematografico()
    {}
}
public class Main
{
    static HashMap<Integer,Filme> mapFilme = new HashMap<Integer,Filme>();
    static ArrayList<Filme> listaFilme = new ArrayList<Filme>();
    static ArrayList<Actor> listaActor = new ArrayList<Actor>();
    public static void main(String[] args)
    {
        long start = System.currentTimeMillis();
        parseMovieFiles();
        getMovies();
        long end = System.currentTimeMillis();
        System.out.println("(demorou " + (end - start) + " ms)");
        Scanner in = new Scanner(System.in);
        String line = in.nextLine();
        while ((line != null && !line.equals("QUIT")))
        {
            long start1 = System.currentTimeMillis();
            String result = executeQuery(line);
            long end1 = System.currentTimeMillis();
            System.out.println(result);
            System.out.println("(demorou " + (end1 - start1) + " ms)");
            line = in.nextLine();
        }
    }
    public static void parseMovieFiles()
    {
        HashSet<Integer> hashFilmes = new HashSet<Integer>();
        ArrayList<GeneroCinematografico> listaGeneros = new ArrayList<GeneroCinematografico>();
        String nomeFileMovies = "deisi_movies.txt";
        String nomeFileActors = "deisi_actors.txt";
        String nomeFileGenres = "deisi_genres.txt";
        // leitura dos Filmes
        lerFilmes(nomeFileMovies,hashFilmes);
        // leitura dos Actores
        lerAtores(nomeFileActors,hashFilmes);
        // leitura dos generos
        lerGeneros(nomeFileGenres, listaGeneros,hashFilmes);
    }
    public static void lerFilmes(String nomeFileFilmes, HashSet<Integer> hash)
    {
        try
        {
            BufferedReader reader = new BufferedReader(new FileReader(nomeFileFilmes));
            StringBuffer strinBuffer = new StringBuffer();
            String linha = null;
            // enquanto o ficheiro tiver linhas não-lidas
            while ((linha = reader.readLine()) != null) {
                
		System.out.println("Linha: " + linha);
		// throw new Exception("Linha: " + linha);

		// ler uma linha do ficheiro
                strinBuffer.append(linha).append("\n");
                // partir a linha no caractere separador
                String dados[] = linha.split(",");
                if (dados.length == 7)
                {
                    int id = Integer.parseInt(dados[0]);
                    if(hash.contains(id) == false)
                    {
                        String titulo = dados[1];
                        String dadosData[] = dados[2].split("-");
                        DataLancamento data = new DataLancamento(Integer.parseInt(dadosData[2]), Integer.parseInt(dadosData[1]), Integer.parseInt(dadosData[0]));
                        int orcamento = Integer.parseInt(dados[3]);
                        double duracao = Double.parseDouble(dados[4]);
                        double media = Double.parseDouble(dados[5]);
                        int nrvotos = Integer.parseInt(dados[6]);
                        Filme filmes = new Filme(id, titulo, data, orcamento, duracao, media, nrvotos);
                        hash.add(id);
                        listaFilme.add(filmes);
                        mapFilme.put(id,filmes);
                    }
                }
            }
            reader.close();
        } catch (FileNotFoundException exception) {
            String mensagem = "Erro: o ficheiro " + nomeFileFilmes + " nao foi encontrado. ";
            System.out.println(mensagem);
        } catch (IOException ex) {
            System.out.println(ex);
        }
    }
    public static void lerAtores(String nomeFileActors,HashSet<Integer> hashFilme)
    {
        HashSet<Integer> hash = new HashSet<Integer>();
        try (BufferedReader reader = new BufferedReader(new FileReader(nomeFileActors)))
        {
            StringBuffer strinBuffer = new StringBuffer();
            String linha = null;
            // enquanto o ficheiro tiver linhas não-lidas
            while ((linha = reader.readLine()) != null) {
                // ler uma linha do ficheiro
                strinBuffer.append(linha).append("\n");
                // partir a linha no caractere separador
                String dados[] = linha.split(",");
                if (dados.length == 4)
                {
                    int idActor = Integer.parseInt(dados[0]);
                    String nome = dados[1];
                    boolean genero = Boolean.parseBoolean(dados[2]);
                    int idFilme = Integer.parseInt(dados[3]);
                    Actor Ator = new Actor(idActor, nome, genero);
                    if(hash.contains(idActor) == false)
                    {
                        hash.add(idActor);
                        listaActor.add(Ator);
                    }
                    if(hashFilme.contains(idFilme) == true)
                    {
                        Filme aux = mapFilme.get(idFilme);
                        HashSet<Integer> hashaux = new HashSet<>();
                        for(int i = 0;i<aux.atores.size();i++)
                        {
                            Actor ator = aux.atores.get(i);
                            hashaux.add(ator.idActor);
                        }
                        if(hashaux.contains(idActor)==false)
                        {
                            aux.atores.add(Ator);
                        }
                    }

                }
            }
            reader.close();
        } catch (FileNotFoundException exception) {
            String mensagem = "Erro: o ficheiro " + nomeFileActors + " nao foi encontrado. ";
            System.out.println(mensagem);
        } catch (IOException ex) {
            System.out.println(ex);
        }

    }
    public static void lerGeneros(String nomeFileGenres, ArrayList<GeneroCinematografico> listaGeneros, HashSet<Integer> hashFilme)
    {
        HashSet<String> hash = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(nomeFileGenres)))
        {
            StringBuffer strinBuffer = new StringBuffer();
            String linha = null;
            // enquanto o ficheiro tiver linhas não-lidas
            while ((linha = reader.readLine()) != null) {
                // ler uma linha do ficheiro
                strinBuffer.append(linha).append("\n");
                // partir a linha no caractere separador
                String dados[] = linha.split(",");
                if (dados.length == 2)
                {
                    String nome = dados[0];
                    int idFilme = Integer.parseInt(dados[1]);
                    GeneroCinematografico generos = new GeneroCinematografico(nome);
                    if(hash.contains(nome) == false)
                    {
                        hash.add(nome);
                        listaGeneros.add(generos);
                    }
                    if(hashFilme.contains(idFilme) == true)
                    {
                        Filme aux = mapFilme.get(idFilme);
                        HashSet<String> hashaux = new HashSet<>();
                        for(int i = 0;i<aux.generos.size();i++)
                        {
                            GeneroCinematografico genero = aux.generos.get(i);
                            hashaux.add(genero.nome);
                        }
                        if(hashaux.contains(nome)==false)
                        {
                            aux.generos.add(generos);
                        }
                    }
                }
            }
            reader.close();
        } catch (FileNotFoundException exception) {
            String mensagem = "Erro: o ficheiro " + nomeFileGenres + " nao foi encontrado. ";
            System.out.println(mensagem);
        } catch (IOException ex) {
            System.out.println(ex);
        }
    }
    public static ArrayList<Filme> getMovies()
    {
        System.out.println("\n");
        System.out.println("\n");
        System.out.println("\n");
        System.out.println("\n");
        System.out.println("\n");
        System.out.println("\n");
        System.out.println("----------------------------------------");
        System.out.println("A QUANTIDADE DE FILMES E: "+listaFilme.size());
        System.out.println("----------------------------------------");
        return listaFilme;
    }
    static String executeQuery(String query) {
        if (query.contains(" ")) {
            String[] dados = query.split(" ");
            if (dados[0].equals("COUNT_MOVIES_YEAR"))// FEITO
            {
               return countMoviesYear(dados);
            } else if (dados[0].equals("COUNT_MOVIES_ACTOR")) // FEITO
            {
                return countMoviesActor(dados);
            } else if (dados[0].equals("COUNT_MOVIES_ACTORS"))// FEITO
            {
                return countMoviesActors(dados);
            }
            else if (dados[0].equals("GET_TITLES_YEAR"))// FEITO
            {
                return getTitlesYear(dados);
            } else if (dados[0].equals("HARD_MODE_ON_1"))// FEITO RETIRAR AS || DO ULTIMO
            {
                return hardModeOn1(dados);
            } else if (dados[0].equals("GET_TOP_VOTED_TITLES_YEAR"))// FEITO ficheiro grande erro 2011 -1 explode no 1561
            {
                return getTopVotedTitlesYear(dados);
            } else if (dados[0].equals("COUNT_MOVIES_ACTOR_YEAR"))// FEITO
            {
                return countMoviesActorYear(dados)+"";
            } else if (dados[0].equals("GET_TOP_ACTOR_YEAR"))//FEITO
            {
               return getTopActorYear(dados);
            } else if (dados[0].equals("COUNT_MOVIES_YEAR_GENRE"))//FEITO
            {
                return countMoviesYearGenre(dados)+"";
            } else if (dados[0].equals("GET_MALE_RATIO_YEAR"))//FEITO
            {
                return getMaleRatioYear(dados);
            } else if (dados[0].equals("COUNT_MOVIES_WITH_MANY_ACTORS"))//FEITO
            {
                return countMoviesWithManyActors(dados)+"";
            } else if (dados[0].equals("GET_TOP_ACTORS_BY_GENRE"))//FEITO
            {
                return getTopActorsByGenre(dados);
            } else if (dados[0].equals("INSERT_ACTOR"))//FEITO
            {
                return insertActor(dados);
            } else if (dados[0].equals("REMOVE_ACTOR"))//FEITO
            {
                return removeActor(dados);
            } else
            {
                return "Query com formato inválido. Tente novamente.";
            }
        }
        return "Query com formato inválido. Tente novamente.";
    }
    public static String countMoviesYear(String dados[])
    {
        int cnt = 0;
        for (int idx = 0; idx < listaFilme.size(); idx++)
        {
            if (dados[1].equals(Integer.toString(listaFilme.get(idx).data.ano)))
            {
                cnt++;
            }
        }
        return ""+cnt;
    }
    public static String countMoviesActor(String dados[])
    {
        String nomeActor = "";
        for(int i = 1;i<dados.length;i++)
        {
            nomeActor+=dados[i];
            if(i < dados.length-1)
            {
                nomeActor+=" ";
            }
        }
        int cnt = 0;
        for (int idx = 0; idx < listaFilme.size(); idx++)
        {
            ArrayList<Actor> listaAtor = listaFilme.get(idx).atores;
            for (int idx1 = 0; idx1 < listaAtor.size(); idx1++) {
                Actor ator = listaAtor.get(idx1);
                if (ator.nome.equals(nomeActor)) {
                    cnt++;
                }
            }
        }
        return "" + cnt;
    }
    public static String countMoviesActors(String dados[])
    {

       String [] Atores = splitAtores(dados);
       int cntFilmes=0;
       for (int idx = 0; idx < listaFilme.size(); idx++)
       {
            int cntAtores = 0;
            ArrayList<Actor> listaAtor = listaFilme.get(idx).atores;

            boolean a = false, b = false;

            for (int idx1 = 0; idx1 < listaAtor.size(); idx1++)
            {
                Actor ator = listaAtor.get(idx1);
                if(ator.nome.equals(Atores[0])) {
                    a = true;
                }
                if(ator.nome.equals(Atores[1])) {
                    b = true;
                }
            }
            if(a && b)
            {
                cntFilmes++;
            }
        }
        return ""+cntFilmes;
    }
    public static String [] splitAtores(String dados[])// Função para separar os nomes dos atores num array
    {
        String [] Atores = new String[listaActor.size()];
        String Actores = "";
        for(int i = 1; i < dados.length;i++)
        {
            Actores+=dados[i];
            if(i < dados.length-1)
            {
                Actores+=" ";
            }
        }
        return Atores = Actores.split(";");
    }
    public static  String getTitlesYear(String dados[])
    {
        String result = "";
        for (int idx = 0; idx < listaFilme.size(); idx++)
        {
            Filme filme = listaFilme.get(idx);
            if (Integer.parseInt(dados[1]) == filme.data.ano)
            {
                result += filme.titulo+"||";
            }
        }
        if(result!="")
        {
            return  result.substring(0,result.length()-2);
        }
        else
        {
            return result;
        }
    }
    public static boolean anoMaior(Filme filme,Filme filmeAux)
    {
        boolean anoMaior = false;
        int dias = filme.data.ano*365+filme.data.mes*30+filme.data.dia;
        int diasAux = filmeAux.data.ano*365+filmeAux.data.mes*30+filmeAux.data.dia;
        if(dias>diasAux)
        {
            anoMaior=true;
        }
        return anoMaior;
    }
    public static boolean atorEmComumFilme(Filme filme, Filme filmeAux)
    {
        boolean  existe = false;
        for(int i = 0; i < filme.atores.size();i++)
        {
            Actor ator = filme.atores.get(i);
            for(int j=0;j<filmeAux.atores.size();j++)
            {
                Actor atorAux = filmeAux.atores.get(j);
                if(atorAux.nome.equals(ator.nome))
                {
                    existe = true;
                    break;
                }
            }
        }
        return existe;
    }
    public static String hardModeOn1(String dados[])
    {
        String result ="";
        Filme filmePassado = null;
        boolean existe=false;
        boolean existeAtorComum=false;
        for(int idx=0;idx < listaFilme.size();idx++)
        {
            filmePassado = listaFilme.get(idx);
            if(filmePassado.id==Integer.parseInt(dados[1]))
            {
                existe = true;
                break;
            }
        }
        if(existe == true)
        {
            for (int idx = 0; idx < listaFilme.size(); idx++)
            {
                Filme filme = listaFilme.get(idx);
                if(filme.id != filmePassado.id)
                {
                    boolean anoMaior = anoMaior(filme,filmePassado);
                    if(anoMaior == true)
                    {
                        if(filme.mediaDeVotos == filmePassado.mediaDeVotos)
                        {
                            existeAtorComum = atorEmComumFilme(filmePassado,filme);
                            if(existeAtorComum == true)
                            {
                                result += filme.titulo+"||";
                            }
                        }
                    }
                }
            }
        }
        if(result!="")
        {
            return  result.substring(0,result.length()-2);
        }
        else
        {
            return result;
        }
    }
    public static String getTopVotedTitlesYear(String dados[])
    {
        int ano = Integer.parseInt(dados[1]);
        int numeroFilmes = Integer.parseInt(dados[2]);
        HashMap<Integer,Filme> map = new HashMap<Integer, Filme>();
        int qtd=0;
        String result="";
        for(int idx = 0; idx < listaFilme.size();idx++)
        {
            Filme filme = listaFilme.get(idx);
            if(filme.data.ano == ano)
            {
                map.put(filme.id,filme);
            }
        }
        int max = map.size();
        for(int i = 0; i < max;i++)
        {
            int maior = maiorVotacao(map);
            if(numeroFilmes == -1)
            {
                result += map.get(maior).titulo + ";" + map.get(maior).mediaDeVotos + "\n";
                map.remove(maior);
            }
            else
            {
                if (qtd < numeroFilmes)
                {
                    result += map.get(maior).titulo + ";" + map.get(maior).mediaDeVotos + "\n";
                    map.remove(maior);
                    qtd++;
                }
            }
        }
        System.out.println();
        return result;
    }
    public static int maiorVotacao(Map<Integer,Filme> mapa)
    {
        double maior=0.0;
        int id=0;
        for(Map.Entry<Integer,Filme> entry : mapa.entrySet())
        {
            if(entry.getValue().mediaDeVotos > maior)
            {
                maior = entry.getValue().mediaDeVotos;
                id = entry.getKey();
            }
        }
        return id;
    }
    public static int countMoviesActorYear(String dados[])
    {
        String nomeActor = "";
        for(int i = 2;i<dados.length;i++)
        {
            nomeActor+=dados[i];
            if(i < dados.length-1)
            {
                nomeActor+=" ";
            }
        }
        int cnt=0;
        for(int i = 0; i<listaFilme.size();i++)
        {
            Filme filme = listaFilme.get(i);
            if(filme.data.ano == Integer.parseInt(dados[1]))
            {
                for(int j = 0; j < filme.atores.size();j++)
                {
                    Actor ator = filme.atores.get(j);
                    if(ator.nome.equals(nomeActor))
                    {
                        cnt++;
                    }
                }
            }
        }
        return cnt;
    }
    public static String getTopActorYear(String dados[])
    {
        String result="";
        HashMap<Integer,Integer> map = new HashMap<Integer, Integer>();
        for(int i = 0; i < listaFilme.size();i++)
        {
            Filme filme = listaFilme.get(i);
            if(filme.data.ano==Integer.parseInt(dados[1]))
            {
                for (int j = 0; j < filme.atores.size(); j++)
                {
                    Actor ator = filme.atores.get(j);
                    if(map.size()==0)
                    {
                        map.put(ator.idActor,1);
                    }
                    else
                    {
                        if (map.containsKey(ator.idActor))
                        {
                            int qtd = map.get(ator.idActor);
                            map.put(ator.idActor, qtd+=1);
                        }
                        else
                        {
                            map.put(ator.idActor, 1);
                        }
                    }
                }
            }
        }
        int idMaior = maiorNumero(map);
        String maior = null;
        for(int i = 0;i < listaActor.size();i++)
        {
            Actor ator = listaActor.get(i);
            if(ator.idActor == idMaior)
            {
                maior = ator.nome;
                break;
            }
        }
        result += maior+";"+map.get(idMaior);
        return result;
    }
    public static int maiorNumero(Map<Integer,Integer> mapa)
    {
        double maior=0.0;
        int id = -1;
        for(Map.Entry<Integer,Integer> entry : mapa.entrySet())
        {
            if(entry.getValue() > maior)
            {
                maior = entry.getValue();
                id = entry.getKey();
            }
        }
        return id;
    }
    public static int countMoviesYearGenre(String dados[])
    {
        String nomeGenero = "";
        int cnt=0;
        int ano = Integer.parseInt(dados[1]);
        for(int i = 2;i<dados.length;i++)
        {
            nomeGenero+=dados[i];
            if(i < dados.length-1)
            {
                nomeGenero+=" ";
            }
        }
        for(int idx = 0; idx < listaFilme.size();idx++)
        {
            Filme filme = listaFilme.get(idx);
            if(filme.data.ano == ano)
            {
                for (int idx1 = 0; idx1 < filme.generos.size(); idx1++) {
                    GeneroCinematografico genero = filme.generos.get(idx1);
                    if (genero.nome.equals(nomeGenero)) {
                        cnt++;
                    }
                }
            }
        }
        return cnt;
    }
    public static String getMaleRatioYear(String dados[])
    {
        int qtdAtores=0;
        int qtdAtoresM=0;
        int qtd=0;
        int [] idActor = new int[listaActor.size()];
        for (int i = 0; i < listaFilme.size();i++)
        {
            Filme filme = listaFilme.get(i);
            if(filme.data.ano == Integer.parseInt(dados[1]))
            {
                for(int j = 0;j < filme.atores.size();j++)
                {
                    Actor ator = filme.atores.get(j);
                    boolean existe = existeAtor(idActor,ator);
                    if(existe == false)
                    {
                        qtdAtores++;
                        if(ator.genero==true)
                        {
                            qtdAtoresM++;
                        }
                        idActor[qtd] = ator.idActor;
                        qtd++;
                    }
                }
            }
        }
        int percentagtem = qtdAtoresM*100/qtdAtores;
        return percentagtem+"%";
    }
    public static boolean existeAtor (int [] ator, Actor Actor)
    {
        boolean existe = false;
        for(int i = 0; i < ator.length;i++)
        {
            if(Actor.idActor == ator[i])
            {
                existe = true;
                break;
            }
        }
        return existe;
    }
    public static int countMoviesWithManyActors(String dados[])
    {
        int cnt=0;
        for(int i = 0; i<listaFilme.size();i++)
        {
            Filme filme = listaFilme.get(i);
            if(filme.atores.size() > Integer.parseInt(dados[1]))
            {
                cnt++;
            }
        }
        return cnt;
    }
    public static String getTopActorsByGenre(String dados[])
    {
        String nomeGenero = "";
        for(int i = 1;i<dados.length;i++)
        {
            nomeGenero+=dados[i];
            if(i < dados.length-1)
            {
                nomeGenero+=" ";
            }
        }
        String result="";
        HashMap<Integer,Integer> map = new HashMap<Integer, Integer>();
        for(int i=0;i< listaFilme.size();i++)
        {
            Filme filme = listaFilme.get(i);
            for(int j = 0; j < filme.generos.size();j++)
            {
                GeneroCinematografico genero = filme.generos.get(j);
                if(genero.nome.equals(nomeGenero))
                {
                    for(int k = 0; k < filme.atores.size();k++)
                    {
                        Actor ator = filme.atores.get(k);
                        if(map.size()==0)
                        {
                            map.put(ator.idActor,1);
                        }
                        else
                        {
                            if (map.containsKey(ator.idActor))
                            {
                                int qtd = map.get(ator.idActor);
                                map.put(ator.idActor,qtd+=1);
                            }
                            else
                            {
                                map.put(ator.idActor, 1);
                            }
                        }
                    }
                }
            }
        }
        if(map.size() > 10)
        {
            for(int i = 0 ; i < 10 ;i++)
            {
                int idMaior = maiorNumero(map);
                String maior = null;
                for(int f = 0;f < listaActor.size();f++)
                {
                    Actor ator = listaActor.get(f);
                    if(ator.idActor == idMaior)
                    {
                        maior = ator.nome;
                        break;
                    }
                }
                result += maior+";"+map.get(idMaior)+"\n";
                map.remove(idMaior);
            }
        }
        else
        {
            int qtd = map.size();
            for(int i = 0 ; i < qtd ;i++)
            {
                int idMaior = maiorNumero(map);
                String maior = null;
                for(int f = 0;f < listaActor.size();f++)
                {
                    Actor ator = listaActor.get(f);
                    if(ator.idActor == idMaior)
                    {
                        maior = ator.nome;
                        break;
                    }
                }
                result += maior+";"+map.get(idMaior)+"\n";
                map.remove(maior);
            }
        }
        return  result;
    }
    public static String insertActor(String dados[])
    {
        String result="";
        String [] dados1 = separarAtor(dados);
        Actor atorFinal = null;
        boolean existeFilme = false;
        boolean existeActor = false;
        boolean Inserido = false;
        for(int i = 0; i < listaFilme.size();i++)
        {
            Filme filme = listaFilme.get(i);
            if (filme.id == Integer.parseInt(dados1[3]))
            {
                existeFilme = true;
                break;
            }
        }
        if(existeFilme == true)
        {
            for(int j = 0; j < listaActor.size();j++)
            {
                Actor ator = listaActor.get(j);
                if (ator.idActor == Integer.parseInt(dados1[0]))
                {
                    existeActor = true;
                }
            }
            if(existeActor == false)
            {
               for(int i = 0; i < listaFilme.size();i++)
               {
                   Filme filme = listaFilme.get(i);
                   if(filme.id==Integer.parseInt(dados1[3]))
                   {
                       Actor ator = new Actor(Integer.parseInt(dados1[0]),dados1[1],Boolean.parseBoolean(dados1[2]));
                       listaActor.add(ator);
                       filme.atores.add(ator);
                       Inserido = true;
                       return "ok";
                   }
               }
               if(Inserido == false)
               {
                   return "Erro";
               }
            }
            else
            {
                return "Erro";
            }
        }
        else
        {
            return "Erro";
        }
        return result;
    }
    public static String[] separarAtor(String dados[])
    {
        String nomeActor = "";
        for(int i = 1;i<dados.length;i++)
        {
            nomeActor+=dados[i];
            if(i < dados.length-1)
            {
                nomeActor+=" ";
            }
        }
        String [] dados1 = nomeActor.split(",");
        return  dados1;
    }
    public static String removeActor(String dados[])
    {
        boolean existeId=false;
        int idAtor = Integer.parseInt(dados[1]);
        for(int i = 0; i < listaActor.size();i++)
        {
            Actor ator = listaActor.get(i);
            if(ator.idActor==idAtor)
            {
                existeId = true;
                break;
            }
        }
        if(existeId == true)
        {
            for(int i = 0; i<listaFilme.size();i++)
            {
                Filme filme = listaFilme.get(i);
                for(int j = 0; j < filme.atores.size();j++)
                {
                    Actor ator = filme.atores.get(j);
                    if(ator.idActor==idAtor)
                    {
                        filme.atores.remove(ator);
                    }
                }
            }
            for(int i = 0; i < listaActor.size();i++)
            {
                Actor ator = listaActor.get(i);
                if(ator.idActor==idAtor)
                {
                    listaActor.remove(ator);
                    return "OK";
                }
            }
        }
        else
        {
            return "Erro";
        }
        return "Erro";
    }
}
