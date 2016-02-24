package com.emc.sa.service.vipr.plugins.tasks;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/*


 String inputNew = "{ \"processDefinitionKey\":\"simpleApprovalProcess\", \"variables\": [{\"name\":\"mailcontent\",\"value\":\"Hello Administrator,A new request for provisioning has arrived.Please view the request and take action.<a href=\\\"http://lglbv240.lss.emc.com:9090/activiti-explorer/#tasks?category=inbox\\\">View and Approve</a>\"},{\"name\":\"to\",\"value\":\"manoj.jain@emc.com\"}]}";

 */

public class ApprovalTaskParam extends ViPRTaskParam {

	String processDefinitionKey;
	public List<NameValueParam> variables = new ArrayList<NameValueParam>();

	public void configurePreLaunchParams(String externalTaskParam) {
		fillCommonParameters(externalTaskParam);
		processDefinitionKey = "createVolumePrelaunchFlow";
		StringBuilder requestBuilder = approvalMailContent();

		NameValueParam mailContentParam = new NameValueParam("mailcontent",
				requestBuilder.toString());
		addVariable(mailContentParam);
		
		NameValueParam receipientParam = new NameValueParam("to",
				"santoshkumar.kavadimatti@emc.com");
		addVariable(receipientParam);
	}

	public void configurePostLaunchParameters(String externalTaskParam) {
		fillCommonParameters(externalTaskParam);
		processDefinitionKey = "createVolumePostlaunchFlow";
		StringBuilder requestBuilder = verificationMailContent();

		NameValueParam mailContentParam = new NameValueParam("mailcontent",
				requestBuilder.toString());
		addVariable(mailContentParam);
		
		NameValueParam receipientParam = new NameValueParam("to",
				"santoshkumar.kavadimatti@emc.com");
		addVariable(receipientParam);
	}

	private void fillCommonParameters(String externalTaskParam) {
		externalTaskParam = "{\"virtualArray\":\"\\\"urn:storageos:VirtualArray:bf8cd0ff-5746-433b-a817-dd45c9f72633:vdc1\\\"\",\"virtualPool\":\"\\\"urn:storageos:VirtualPool:757bb977-8a5b-4e26-9b06-4e6787520c0e:vdc1\\\"\",\"project\":\"\\\"urn:storageos:Project:047dde9b-3049-45ec-8599-0101e10fff7c:global\\\"\",\"name\":\"\\\"MYVOLEXT-21-1\\\"\",\"size\":\"\\\"1\\\"\",\"numberOfVolumes\":\"\\\"1\\\"\",\"consistencyGroup\":\"\\\"\\\"\",\"externalParam\":\"externalParam\"}";
		InputStream inStream = null;
		try {
			inStream = IOUtils.toInputStream(externalTaskParam, "UTF-8");

			String virtualArray = TaskParamParser.getJsonXpathPropert(
					externalTaskParam, "virtualArray", String.class);
			NameValueParam virtualArrayParam = new NameValueParam(
					"Virtual Array", virtualArray);
			addVariable(virtualArrayParam);

			String virtualPool = TaskParamParser.getJsonXpathPropert(
					externalTaskParam, "virtualPool", String.class);
			NameValueParam virtualPoolParam = new NameValueParam(
					"Virtual Pool", virtualPool);
			addVariable(virtualPoolParam);

			String project = TaskParamParser.getJsonXpathPropert(
					externalTaskParam, "project", String.class);
			NameValueParam projectParam = new NameValueParam("Project", project);
			addVariable(projectParam);

			String name = TaskParamParser.getJsonXpathPropert(
					externalTaskParam, "name", String.class);
			NameValueParam nameParam = new NameValueParam("Name", name);
			addVariable(nameParam);

			String size = TaskParamParser.getJsonXpathPropert(
					externalTaskParam, "size", String.class);
			NameValueParam sizeParam = new NameValueParam("Size", size);
			addVariable(sizeParam);

			String numberOfVolumes = TaskParamParser.getJsonXpathPropert(
					externalTaskParam, "numberOfVolumes", String.class);
			NameValueParam numberOfVolumesParam = new NameValueParam(
					"Number of Volumes", numberOfVolumes);
			addVariable(numberOfVolumesParam);

			String consistencyGroup = TaskParamParser.getJsonXpathPropert(
					externalTaskParam, "consistencyGroup", String.class);
			NameValueParam consistencyGroupParam = new NameValueParam(
					"Consistency Group", consistencyGroup);
			addVariable(consistencyGroupParam);

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			IOUtils.closeQuietly(inStream);
		}
	}

	private StringBuilder approvalMailContent() {
		StringBuilder requestBuilder = new StringBuilder(
				"<html><head><style>body {    background-color: #fff;    color: #333;    font-family: \"Helvetica Neue\",Helvetica,Arial,sans-serif;    font-size: 12px;}<\\/style><\\/head>"
						+ " <body><h3> Create Volume Request Approval<\\/h3><hr><div><p style=\"font-size: 12px;\"> Hello Administrator,<\\/p><\\/div><div><p style=\"font-size: 12px;\"> A new request has arrived for creating a volume. Request details:<\\/p><\\/div><table >");// "Hello Administrator,<br>A new request for provisioning has arrived.Please view the request and take action.Request Details : <br> <br>");

		for (NameValueParam nameValueParam : variables) {
			requestBuilder.append("<tr><td align=\"right\" ><b>"
					+ nameValueParam.getName()
					+ " :<\\/b><\\/td><td align=\"left\"> "
					+ nameValueParam.getValue()
					+ "<\\/td><\\/tr><tr><\\/tr><tr><\\/tr>");
		}

		requestBuilder.append("<\\/table>");
		requestBuilder
				.append("<br><br><table align=\"center\"> <tr><td style=\"background-color: #428bca;border-color: border-color: #357ebd;border: 2px solid #45b7af;padding: 10px;text-align: center;\"> <a style=\"display: block;color: #ffffff;font-size: 12px;text-decoration: none;\" href=\"http://localhost:8080/activiti-explorer/#tasks?category=inbox\">Approve Request<\\/a><\\/td><\\/tr></table><\\/body><\\/html>");
		return requestBuilder;
	}

	private StringBuilder verificationMailContent() {
		StringBuilder requestBuilder = new StringBuilder(
				"<html><head><style>body {    background-color: #fff;    color: #333;    font-family: \"Helvetica Neue\",Helvetica,Arial,sans-serif;    font-size: 12px;}<\\/style><\\/head>"
						+ " <body><h3> Create Volume Request Verification<\\/h3><hr><div><p style=\"font-size: 12px;\"> Hello Administrator,<\\/p><\\/div><div><p style=\"font-size: 12px;\"> The following request has been processed by the system. Please verify and confirm. Request details:<\\/p><\\/div><table >");

		for (NameValueParam nameValueParam : variables) {
			requestBuilder.append("<tr><td align=\"right\" ><b>"
					+ nameValueParam.getName()
					+ " :<\\/b><\\/td><td align=\"left\"> "
					+ nameValueParam.getValue()
					+ "<\\/td><\\/tr><tr><\\/tr><tr><\\/tr>");
		}

		requestBuilder.append("<\\/table>");
		requestBuilder
				.append("<br><br><table align=\"center\"> <tr><td style=\"background-color: #428bca;border-color: border-color: #357ebd;border: 2px solid #45b7af;padding: 10px;text-align: center;\"> <a style=\"display: block;color: #ffffff;font-size: 12px;text-decoration: none;\" href=\"http://localhost:8080/activiti-explorer/#tasks?category=inbox\">Confirm<\\/a><\\/td><\\/tr></table><\\/body><\\/html>");
		return requestBuilder;
	}

	public String getProcessDefinitionKey() {
		return processDefinitionKey;
	}

	public void setProcessDefinitionKey(String processDefinitionKey) {
		this.processDefinitionKey = processDefinitionKey;
	}

	public List<NameValueParam> getVariables() {
		return variables;
	}

	public void setVariables(List<NameValueParam> variables) {
		this.variables = variables;
	}

	public void addVariable(NameValueParam variable) {
		if (null == variables) {
			variables = new ArrayList<ApprovalTaskParam.NameValueParam>();
		}
		variables.add(variable);
	}

	class NameValueParam {
		String name;
		String value;

		public NameValueParam() {

		}

		public NameValueParam(String name, String value) {
			this.name = name;
			this.value = value;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}

	}

	public static void main(String[] args) {
		ApprovalTaskParam approvalTaskParam = new ApprovalTaskParam();
		approvalTaskParam.configurePreLaunchParams(null);
		Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();
		String pretJson = prettyGson.toJson(approvalTaskParam);

		System.out.println("Pretty printing: " + pretJson);
	}

}
