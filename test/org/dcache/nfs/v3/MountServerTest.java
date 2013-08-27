package org.dcache.nfs.v3;

import com.google.common.base.Splitter;
import com.google.common.net.InetAddresses;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.dcache.nfs.ExportFile;
import org.dcache.nfs.FsExport;
import org.dcache.nfs.v3.xdr.dirpath;
import org.dcache.nfs.v3.xdr.mountres3;
import org.dcache.nfs.v3.xdr.mountstat3;
import org.dcache.nfs.vfs.Inode;
import org.dcache.nfs.vfs.Stat;
import org.dcache.nfs.vfs.VirtualFileSystem;
import org.dcache.xdr.RpcAuthType;
import org.dcache.xdr.RpcCall;
import org.dcache.xdr.XdrTransport;
import org.junit.Test;
import org.junit.Before;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.BDDMockito.given;

/**
 *
 */
public class MountServerTest {

    private VirtualFileSystem _fs;
    private ExportFile _exportFile;
    private FsExport _export;
    private MountServer _mountServer;

    @Before
    public void setUp() {
        _fs = mock(VirtualFileSystem.class);
        _exportFile = mock(ExportFile.class);
        _export = mock(FsExport.class);
        _mountServer = new MountServer(_exportFile, _fs);
    }

    @Test
    public void testMounFlavors() throws IOException {
        String path = "/some/export";
        mountres3 res = mountServer()
                .withPath(path)
                .exportedTo("192.168.178.33")
                .withSecurity(FsExport.Sec.KRB5).
                accessedFrom("192.168.178.33").
                toMount(path);

        assertEquals(mountstat3.MNT3_OK, res.fhs_status);

        assertTrue(arrayContains(res.mountinfo.auth_flavors, MountServer.RPC_AUTH_GSS_KRB5I));
        assertTrue(arrayContains(res.mountinfo.auth_flavors, MountServer.RPC_AUTH_GSS_KRB5P));
        assertFalse(arrayContains(res.mountinfo.auth_flavors, MountServer.RPC_AUTH_GSS_KRB5));
        assertFalse(arrayContains(res.mountinfo.auth_flavors, RpcAuthType.NONE));
        assertFalse(arrayContains(res.mountinfo.auth_flavors, RpcAuthType.UNIX));
    }

    private static boolean arrayContains(int[] array, int element) {
        for (int i : array) {
            if (i == element) {
                return true;
            }
        }
        return false;
    }

    MountServertestHelper mountServer() throws IOException {
        return new MountServertestHelper();
    }

    private class MountServertestHelper {

        private String path;
        private RpcCall call;

        MountServertestHelper withPath(String path) throws IOException {
            this.path = path;

            Splitter splitter = Splitter.on('/').omitEmptyStrings();
            Inode inode = mock(Inode.class);
            Stat stat = new Stat();
            stat.setMode(0755 | Stat.S_IFDIR);
            stat.setUid(1);
            stat.setGid(1);

            given(_fs.getRootInode()).willReturn(inode);
            given(_fs.getattr(inode)).willReturn(stat);

            for (String pathElement : splitter.split(path)) {
                Inode objectInode = mock(Inode.class);

                given(_fs.lookup(inode, pathElement)).willReturn(objectInode);
                given(_fs.getattr(objectInode)).willReturn(stat);
                given(objectInode.getFileId()).willReturn(pathElement.getBytes());
                inode = objectInode;
            }

            return this;
        }

        MountServertestHelper exportedTo(String client) {
            InetAddress address = InetAddresses.forString("192.168.178.33");
            given(_exportFile.getExport(path, address)).willReturn(_export);
            return this;
        }

        MountServertestHelper withSecurity(FsExport.Sec sec) {
            given(_export.getSec()).willReturn(FsExport.Sec.KRB5I);
            return this;
        }

        MountServertestHelper accessedFrom(String client) {
            InetAddress address = InetAddresses.forString(client);
            this.call = mock(RpcCall.class);
            XdrTransport transport = mock(XdrTransport.class);

            given(transport.getRemoteSocketAddress()).willReturn(new InetSocketAddress(address, 2222));
            given(call.getTransport()).willReturn(transport);
            return this;
        }

        mountres3 toMount(String path) {
            return _mountServer.MOUNTPROC3_MNT_3(call, new dirpath(path));
        }
    }

}
