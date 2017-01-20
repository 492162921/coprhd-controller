package com.emc.ctd.vipr.workflow.tasks;


import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Logger;

import com.emc.storageos.model.tenant.TenantResponse;
import com.emc.vipr.client.ClientConfig;
import com.emc.vipr.client.ViPRCoreClient;

public class ViPRClientFactory {
	
	
	String host;
	Integer port;
	String userName; 
	String userPassword;
	
	boolean isLoggedin=false;
	String authToken=null;
	
	
	private  ViPRCoreClient viprClient=null;
	
	
	private static Logger log = Logger.getLogger(ViPRClientFactory.class);
	
	private static volatile ViPRClientFactory instance = null;

    private ViPRClientFactory() { }

    public static ViPRCoreClient getViprClient(String host, Integer port,String userName, String userPassword )  throws Exception{
        if (instance == null) {
            synchronized (ViPRClientFactory.class) {
                if (instance == null) {
                    instance = new ViPRClientFactory();
                }
            }
        }
        return instance.login(host, port, userName, userPassword);
    }
	
    public static ViPRCoreClient getViprClient()  {
        if (instance == null) {
            synchronized (ViPRClientFactory.class) {
                if (instance == null) {
                    instance = new ViPRClientFactory();
                    try {
						instance.login("localhost", 4443, "root", "Dangerous@123");
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
                }
            }
        }    
    	return instance.getClient();
    }
    

	private ViPRCoreClient getClient() {
		return viprClient;
	}

	synchronized private ViPRCoreClient login(String host, Integer port,String userName, String userPassword ) throws Exception{
		
		if  ( host.equals(this.host) && (this.port==port) && isLoggedin && viprClient != null ){
			return viprClient;
		}
		
		ClientConfig clientConfig=new ClientConfig()
		.withHost(host).withRequestLoggingEnabled()
		.withHost(host)
		.withPort(port > 0 ? port : ClientConfig.DEFAULT_API_PORT)
		.withMaxRetries(10)
		.withIgnoringCertificates(true);
		
		viprClient = new ViPRCoreClient(clientConfig);
		String token = viprClient.auth().login(userName, userPassword);
		if (token !=null ){
			isLoggedin=true;
			log.info("Login successful for host :" + host);
		} else {
			isLoggedin=false;
		}
		if (viprClient ==null || !isLoggedin ){
			viprClient=null;
			throw new Exception("Login failed for host :" + host);
		}
	
		return viprClient;
	}
	

	
	public  void logout() {
		if (viprClient != null)  {
			viprClient.auth().logout();
			viprClient = null;
			isLoggedin=false;
		}
		
		log.debug("viprClient is logout");
	}
	

	
	@Override
	protected void finalize() throws Throwable {
		logout();
		super.finalize();
	}
	
	
	public static void main(String[] args) throws Exception {
		
		String host = "lglbv240.lss.emc.com";
		Integer port = 4443;
		String user ="root";
		String password ="Dangerous@123";

		
		ViPRCoreClient viprClient = ViPRClientFactory.getViprClient(host, port, user, password);
		TenantResponse tenet = viprClient.getUserTenant();
		
		
    	
    	//String host="lglbv239.lss.emc.com";
    	//Integer port=4443;
    	//String userName="manoj@cli.vipr"; 
    	//String userName="root"; 
    	//String userPassword="Dangerous@123";
		//ViPRClientFactory.getViprClient(host,  port, userName, userPassword);
    	
        	

    	
//		
//    	 TenantInfo[] tenantInfos = new VCEProvisionHelper().getUserTenants();
//    	 
//    	 for(int i=0 ; i < tenantInfos.length ; i++){
//    		 System.out.println(tenantInfos[i].toString());
//    	 }
//    	
//		
//		List<TenantOrgRestRep> tenants = ViPRClientUtils.getUserTenants();
//		
//		for (TenantOrgRestRep tenant : tenants){
//			
//
//			System.out.println("************tenant "+tenant.getName()+tenant.getId());
//			
//			
//		}
//	
//
//		
//		
//
//		List<ProjectRestRep> projects = ViPRClientUtils.getProjectsByUserTenant();
//		for (ProjectRestRep project : projects){
//			
//			System.out.println("**************project "+project.getName() + project.getId());
//			
//		}
//		
//		List<VirtualArrayRestRep> varrays = ViPRClientUtils.getVirtualArraysForTenant();
//		
//		for (VirtualArrayRestRep varray : varrays){
//			System.out.println("***********varray "+varray.getName() +varray.getId());
//			
//			
//		}
//		
//		List<BlockVirtualPoolRestRep> blockVpools = ViPRClientUtils.getBlockVirtualPools();
//		
//		for (BlockVirtualPoolRestRep blockVpool : blockVpools){
//			System.out.println("***********blockVpool "+blockVpool.getName() +blockVpool.getId());
//			
//		}
//
//		List<FileVirtualPoolRestRep> fileVpools = ViPRClientUtils.getFileVirtualPools();
//
//		for (FileVirtualPoolRestRep fileVpool : fileVpools){
//			System.out.println("***********fileVpool "+fileVpool.getName() +fileVpool.getId());
//		}
//	
//		URI tenantURI=ViPRClientUtils.getUserTenant().getId();
//		
//		System.out.println("MANOJ tenantURI is "+tenantURI.toString());
//		
//		
//		TenantInfo tenantInfo = new VCEProvisionHelper().getUserTenants()[0];
//		 
//		tenantURI = tenantInfo.getId();
//
//		 ClusterInfo cluster = new VCEProvisionHelper().createCluster(tenantURI, "VCECluster3");
//		
//		System.out.println("***********createCluster "+cluster.getId());
//		
		
//		ArrayList<URI> volumeIds = new ArrayList<URI>(   Arrays.asList(new URI("urn:storageos:Volume:adcdfc8d-a8e3-4b0b-865a-d0450d93353b:vdc1") ));
//		URI hostUri= new URI("urn:storageos:Host:37c7e322-dede-4252-abe8-4c102802d43f:vdc1");
//		Task<ExportGroupRestRep> export = ViPRClientUtils.createHostOrClusterExport(varrayId,projectId, volumeIds ,-1,false,hostUri);
	
		/*
		 Virtual Pool: urn:storageos:VirtualPool:dbda8252-375b-4129-8e83-71bedd75cdf4:vdc1, 
		 Virtual Array: urn:storageos:VirtualArray:265be179-1ed0-42c3-b9a2-5dc1d6fda4eb:vdc1, 
		 Project: urn:storageos:Project:3701e6cc-c60f-49c7-906d-831eb8f90b0f:global, 
		 Volumes: [Name: MANOJ-VOL, Size: 2.0, Count: 1]
		 */		

   
		System.out.println("***********createVolume Start");
		
    	String tenantId= "urn:storageos:TenantOrg:fea5951f-1d80-45d4-bae3-1da3b4616391:global";
		

		String projectId = ( "urn:storageos:Project:3701e6cc-c60f-49c7-906d-831eb8f90b0f:global");

		String varrayId = ("urn:storageos:VirtualArray:265be179-1ed0-42c3-b9a2-5dc1d6fda4eb:vdc1");


		String blockVpoolId = ("urn:storageos:VirtualPool:dbda8252-375b-4129-8e83-71bedd75cdf4:vdc1");
		
		String volName ="MANOJ-VOL-EXP";
		
		String volSize=String.valueOf( (Double) (2.0*1024*1024*1024));
		String count = "1";
		
		String consistencyGroupId = null;
		
//		VolumeCreationResult[] volResults = new VCEProvisionHelper().createVolumes(varrayId, projectId, blockVpoolId, volName, volSize, count, consistencyGroupId);
		
		System.out.println("***********createCluster End");
		
		
//		
//		String hostOrClusterId="urn:storageos:Cluster:0afc607c-fe83-420c-93e2-b00957df3408:vdc1";
//		String isCluster = "true";
//		String hlu= "-1";
//		String[] volumeIds = new String[volResults.length];
//		
//		for (int i=0; i<volResults.length; i++) {
//			volumeIds[i]=volResults[i].getVolId();
//		}
//		
//		VolumeExportResult[] export = new VCEProvisionHelper().createHostOrClusterExport(varrayId, projectId, volumeIds, hlu, isCluster, hostOrClusterId);
//		
//		System.out.println("Volume exported to host "+export);
		



	}

}
