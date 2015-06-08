package com.indra.expurgo;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import com.jezhumble.javasysmon.JavaSysMon;

public class Main {

	static Path txtParametro = Paths.get("D:\\SMAP\\ExpurgoLOG\\ConfiguracaoExpurgo.ini");
	static String diretorioPrincipal;
	static Integer qtdDiasExclusao; // Quantidade minima de dias de existencia do arquivo para ser excluido
	static Integer contLogDeletados = 0;
	static Integer hora;
	static Integer minuto;
	static ArrayList<String> arquivoConfiguracao = new ArrayList<String>();
	static ArrayList<String> pastasNaoPercorrer = new ArrayList<String>();
	static ArrayList<String> arquivosNaoExcluir = new ArrayList<String>();
	static ArrayList<String> PastasAPercorrer = new ArrayList<String>();

	public static void main(String[] args) {
		JavaSysMon javaPid = new JavaSysMon();
		int pid = javaPid.currentPid();
		System.out.println("=======================\n|ExpurgoLOG PID: " + pid + " |\n=======================\n\n");
		if (lerArquivoConfig()) {
			Calendar horaExecucao = Calendar.getInstance();
			horaExecucao.set(Calendar.HOUR_OF_DAY, hora); // Hora da Execução
			horaExecucao.set(Calendar.MINUTE, minuto); // Minuto da Execução
			horaExecucao.set(Calendar.SECOND, 00); // Segundo da Execução
			Date hora = horaExecucao.getTime();

			Timer timer = new Timer();
			timer.schedule(new TimerTask() {
				@Override
				public void run() {

					if (lerArquivoConfig()) {
						System.out.println("Inicializando Expurgo de LOGs agendado...");
						System.out.println("=========================================");
						for (int i = 0; i < PastasAPercorrer.size(); i++) {
							Path pastaDefault = Paths.get(diretorioPrincipal + "\\" + PastasAPercorrer.get(i));
							if (Files.exists(pastaDefault, LinkOption.NOFOLLOW_LINKS)) {
								percorrerDiretorio(pastaDefault); // Método que percorre as Pastas dentro do diretório principal
							} else {
								System.err.println("Pasta \"" + PastasAPercorrer.get(i) + "\" Não encontrada.");
								;
							}
						}
						System.out.println("=========================================");
						System.out.println("Expurgo finalizado, " + contLogDeletados + " arquivos deletados.");
					}
				}
			}, hora);
		}
	}

	private static void percorrerDiretorio(Path path) {

		try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {

			for (Path caminho : stream) {
				if (Files.isDirectory(caminho)) {
					if (!pastasNaoPercorrer.contains(caminho.getFileName().toString().toLowerCase())) {
						percorrerDiretorio(caminho); // Recursividade para percorrer as pastas dentro de uma pasta
					}
				} else if (caminho.getFileName().toString().toLowerCase().endsWith(".log")
						|| caminho.getFileName().toString().toLowerCase().endsWith(".txt")) { // Verifica extensão do arquivo

					Boolean podeExcluir = true;
					for (String inicioNomeArquivo : arquivosNaoExcluir) {
						if (caminho.getFileName().toString().toLowerCase().startsWith(inicioNomeArquivo.toString())) {
							podeExcluir = false;
						}
					}

					if (podeExcluir) {
						BasicFileAttributes atributosArquivo = Files.readAttributes(caminho, BasicFileAttributes.class);
						if (verificaDataCriacao(atributosArquivo.lastModifiedTime().toMillis())) {
							try {
								if (Files.deleteIfExists(caminho)) { // Verifica se o arquivo realmente foi excluído
									System.out.println("Log excluído: " + caminho.getParent() + "\\" + caminho.getFileName());
									contLogDeletados++;
								}
							} catch (AccessDeniedException e) {
								System.err.println("Arquivo não excluído por falta de permissão: " + caminho.getParent() + "\\"
										+ caminho.getFileName());
							} catch (FileSystemException fse) {
								System.err.println("Arquivo não excluído por estar sendo utilizado em outro processo: " + caminho.getParent() + "\\"
										+ caminho.getFileName());
							}
						}
					}
				}
			}
		} catch (IOException e) {
			System.err.print("Erro: ");
			e.printStackTrace();
		}
	}

	private static boolean lerArquivoConfig() {

		Boolean validado = false;
		if (Files.notExists(txtParametro, LinkOption.NOFOLLOW_LINKS)) {
			System.err.println("O arquivo " + txtParametro.toString() + " não foi encontrado. \n");
			try { Thread.sleep(10000);} catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
		} else {
			try {
				arquivoConfiguracao = (ArrayList<String>) Files.readAllLines(txtParametro, Charset.defaultCharset());
				for (int i = 0; i < arquivoConfiguracao.size(); i++) {
					String linha = arquivoConfiguracao.get(i);

					if (linha.contains("<Diretorio>") && linha.contains("</Diretorio>")) {
						diretorioPrincipal = linha.replace("<Diretorio>", "").replace("</Diretorio>", "").replace("/", "\\");
						if (diretorioPrincipal.endsWith("\\")) {
							diretorioPrincipal = diretorioPrincipal.substring(0, diretorioPrincipal.length() - 1);
						}
					} else if (linha.contains("<DiasExistencia>") && linha.contains("</DiasExistencia>")) {
						qtdDiasExclusao = Integer.parseInt(linha.replace("<DiasExistencia>", "").replace("</DiasExistencia>", ""));
					} else if (linha.contains("<Pasta>") && linha.contains("</Pasta>")) {
						PastasAPercorrer.add(linha.replace("<Pasta>", "").replace("</Pasta>", ""));
					} else if (linha.contains("<PastaNaoPercorrer>") && linha.contains("</PastaNaoPercorrer>")) {
						pastasNaoPercorrer.add(linha.replace("<PastaNaoPercorrer>", "").replace("</PastaNaoPercorrer>", "").toLowerCase());
					} else if (linha.contains("<Arquivo>") && linha.contains("</Arquivo>")) {
						arquivosNaoExcluir.add(linha.replace("<Arquivo>", "").replace("</Arquivo>", "").toLowerCase());
					} else if (linha.contains("<Hora>") && linha.contains("</Hora>")) {
						hora = Integer.parseInt(linha.replace("<Hora>", "").replace("</Hora>", "").toLowerCase());
					} else if (linha.contains("<Minuto>") && linha.contains("</Minuto>")) {
						minuto = Integer.parseInt(linha.replace("<Minuto>", "").replace("</Minuto>", "").toLowerCase());
					}
				}
				if (validarDadosConfiguracao()) {
					validado = true;
				}
			} catch (IOException e) {
				e.printStackTrace();
				System.err.println("O arquivo ExpurgoLogParametro.txt não pode ser lido. ");
			} catch (NumberFormatException NFE) {
				System.err.println("<Hora>, <Minuto> e <DiasExistencia> devem ser numeros;\n");
			}
		}
		return validado;
	}

	private static boolean verificaDataCriacao(Long dataCriacao) {

		Date dataArquivo = new Date(dataCriacao);
		Date dataAtual = new Date();
		long diferencaDias = (dataAtual.getTime() - dataArquivo.getTime()) / (1000 * 60 * 60 * 24);
		if (diferencaDias > qtdDiasExclusao) {
			return true;
		} else {
			return false;
		}
	}

	private static Boolean validarDadosConfiguracao() {
		StringBuilder mensagemErro = new StringBuilder("Arquivo ConfiguracaoExpurgo.ini contém entradas invalidas:\n");
		Boolean validado = true;

		if (qtdDiasExclusao == null || qtdDiasExclusao < 1) {
			mensagemErro.append("-<DiasExistencia> deve ser um numero inteiro positivo;\n");
			validado = false;
		}
		if (diretorioPrincipal == null || diretorioPrincipal.contains("\\\\")) {
			mensagemErro.append("-Nao deve haver barras duplas no <Diretorio>;\n");
			validado = false;
		}
		if (PastasAPercorrer.size() < 1) {
			mensagemErro.append("-Deve Existir no minimo uma <Pasta> mapeada;\n");
			validado = false;
		}
		if (hora < 0 || hora > 23 || minuto < 0 || minuto > 59) {
			mensagemErro.append("-<Hora> e <Minuto> devem formar uma Hora valida;\n");
			validado = false;
		}
		if (validado) {
			return validado;
		} else {
			System.err.println(mensagemErro);
			return false;
		}
	}

}
