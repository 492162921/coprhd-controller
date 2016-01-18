#!/usr/bin/env python
import distutils.core

distutils.core.setup(
    name='SosCli',
    description='ViPR commands line interface for accessing ViPR appliance',
    classifiers=[
        'Development Status :: Production/Stable',
        'Environment :: Console',
        'Intended Audience :: All Users',
        'License :: EMC license',
        'Operating System :: OS Independent',
        'Programming Language :: Python',
        'Topic :: Software Development',
    ],
    data_files=[('', ['authentication.py',
                      'loginfailedip.py',
                      'viprcli.py',
                      'common.py',
                      'virtualpool.py',
                      'exportgroup.py',
                      'fileshare.py',
                      'metering.py',
                      'monitoring.py',
                      'virtualarray.py',
                      'project.py',
                      'snapshot.py',
                      'storagepool.py',
                      'storageport.py',
                      'storagesystem.py',
                      'tenant.py',
                      'network.py',
                      'volume.py',
                      'key.py',
                      'keypool.py',
                      'keystore.py',
                      'truststore.py',
                      'sysmanager.py',
                      'viprcli.bat',
                      'protectionsystem.py',
                      'networksystem.py',
                      'consistencygroup.py',
                      'host.py',
                      'hostinitiators.py',
                      'hostipinterfaces.py',
                      'cluster.py',
                      'vcenter.py',
                      'vcenterdatacenter.py',
                      'sysmgrcontrolsvc.py',
                      'urihelper.py',
                      'approval.py',
                      'assetoptions.py',
                      'catalog.py',
                      'executionwindow.py',
                      'order.py',
                      'tag.py',
                      'quota.py',
                      'storageprovider.py',
                      'virtualdatacenter.py',
                      'sanfabrics.py',
		      'computeimage.py',
		      'computelement.py',
                      'bucket.py',		      
		      'computesystem.py',
		      'computevpool.py',
                      'vnasserver.py',
		      'computeimageserver.py',
                      'quotadirectory.py',
                      'volumegroup.py',
                      'ipsecmanager.py',
                      'snapshotsession.py']
                 )]
)
