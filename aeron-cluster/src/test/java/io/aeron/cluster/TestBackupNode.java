/*
 * Copyright 2014-2019 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.aeron.cluster;

import io.aeron.archive.Archive;
import io.aeron.archive.client.AeronArchive;
import io.aeron.driver.MediaDriver;
import org.agrona.CloseHelper;

public class TestBackupNode
{
    private final ClusterBackupMediaDriver clusterBackupMediaDriver;
    private final Context context;
    private boolean isClosed = false;

    TestBackupNode(final Context context)
    {
        clusterBackupMediaDriver = ClusterBackupMediaDriver.launch(
            context.mediaDriverContext,
            context.archiveContext,
            context.clusterBackupContext);

        this.context = context;
    }

    public void close()
    {
        if (!isClosed)
        {
            CloseHelper.close(clusterBackupMediaDriver);

            if (null != clusterBackupMediaDriver)
            {
                clusterBackupMediaDriver.mediaDriver().context().deleteAeronDirectory();
            }

            isClosed = true;
        }
    }

    void cleanUp()
    {
        if (null != clusterBackupMediaDriver)
        {
            clusterBackupMediaDriver.clusterBackup().context().deleteDirectory();
            clusterBackupMediaDriver.archive().context().deleteArchiveDirectory();
        }
    }

    boolean isClosed()
    {
        return isClosed;
    }

    ClusterBackup.State state()
    {
        return ClusterBackup.State.get((int)clusterBackupMediaDriver.clusterBackup().context().stateCounter().get());
    }

    static class Context
    {
        final MediaDriver.Context mediaDriverContext = new MediaDriver.Context();
        final Archive.Context archiveContext = new Archive.Context();
        final AeronArchive.Context aeronArchiveContext = new AeronArchive.Context();
        final ClusterBackup.Context clusterBackupContext = new ClusterBackup.Context();
    }
}
