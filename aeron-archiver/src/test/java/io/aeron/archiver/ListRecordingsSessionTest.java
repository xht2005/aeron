package io.aeron.archiver;

import io.aeron.Publication;
import io.aeron.archiver.codecs.RecordingDescriptorDecoder;
import org.agrona.CloseHelper;
import org.agrona.IoUtil;
import org.agrona.collections.MutableInteger;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static io.aeron.archiver.codecs.RecordingDescriptorDecoder.BLOCK_LENGTH;
import static io.aeron.archiver.codecs.RecordingDescriptorDecoder.SCHEMA_VERSION;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class ListRecordingsSessionTest
{
    private static final int SEGMENT_FILE_SIZE = 128 * 1024 * 1024;
    private final RecordingDescriptorDecoder recordingDescriptorDecoder = new RecordingDescriptorDecoder();
    private long[] recordingIds = new long[3];
    private File archiveDir = TestUtil.makeTempDir();
    private Catalog catalog;
    private final long correlationId = 1;
    private final Publication controlPublication = mock(Publication.class);
    private final ControlSessionProxy controlSessionProxy = mock(ControlSessionProxy.class);
    private final ControlSession controlSession = mock(ControlSession.class);;

    @Before
    public void before() throws Exception
    {
        catalog = new Catalog(archiveDir, null, 0);
        recordingIds[0] = catalog.addNewRecording(
            0L, 0, SEGMENT_FILE_SIZE, 4096, 1024, 6, 1, "channelG", "channelG?tag=f", "sourceA");
        recordingIds[1] = catalog.addNewRecording(
            0L, 0, SEGMENT_FILE_SIZE, 4096, 1024, 7, 2, "channelH", "channelH?tag=f", "sourceV");
        recordingIds[2] = catalog.addNewRecording(
            0L, 0, SEGMENT_FILE_SIZE, 4096, 1024, 8, 3, "channelK", "channelK?tag=f", "sourceB");
    }

    @After
    public void after()
    {
        CloseHelper.quietClose(catalog);
        IoUtil.delete(archiveDir, false);
    }

    @Test
    public void shouldSendAllDescriptors()
    {
        final ListRecordingsSession session = new ListRecordingsSession(
            correlationId,
            controlPublication,
            0,
            3,
            catalog,
            controlSessionProxy,
            controlSession);
        session.doWork();
        assertThat(session.isDone(), is(false));
        when(controlPublication.maxPayloadLength()).thenReturn(8096);
        session.doWork();
        verify(controlSessionProxy, times(3)).sendDescriptor(eq(correlationId), any(), eq(controlPublication));
    }

    @Test
    public void shouldSend2Descriptors()
    {
        final int fromId = 1;
        final ListRecordingsSession session = new ListRecordingsSession(
            correlationId,
            controlPublication,
            fromId,
            2,
            catalog,
            controlSessionProxy,
            controlSession);
        session.doWork();
        assertThat(session.isDone(), is(false));
        when(controlPublication.maxPayloadLength()).thenReturn(8096);
        final MutableInteger counter = new MutableInteger(fromId);
        when(controlSessionProxy.sendDescriptor(eq(correlationId), any(), eq(controlPublication)))
            .then(invocation ->
            {
                final UnsafeBuffer b = invocation.getArgument(1);
                recordingDescriptorDecoder.wrap(b, Catalog.CATALOG_FRAME_LENGTH, BLOCK_LENGTH, SCHEMA_VERSION);
                final int i = counter.intValue();
                assertThat(recordingDescriptorDecoder.recordingId(), is(recordingIds[i]));
                counter.set(i + 1);
                return null;
            });
        session.doWork();
        verify(controlSessionProxy, times(2)).sendDescriptor(eq(correlationId), any(), eq(controlPublication));
    }

    @Test
    public void shouldSend2DescriptorsAndError()
    {
        final ListRecordingsSession session = new ListRecordingsSession(
            correlationId,
            controlPublication,
            1,
            3,
            catalog,
            controlSessionProxy,
            controlSession);
        session.doWork();
        assertThat(session.isDone(), is(false));
        when(controlPublication.maxPayloadLength()).thenReturn(8096);
        session.doWork();
        verify(controlSessionProxy, times(2)).sendDescriptor(eq(correlationId), any(), eq(controlPublication));
        verify(controlSessionProxy).sendDescriptorNotFound(eq(correlationId), eq(3L), eq(3L), eq(controlPublication));
    }

    @Test
    public void shouldSendError()
    {
        final ListRecordingsSession session = new ListRecordingsSession(
            correlationId,
            controlPublication,
            3,
            3,
            catalog,
            controlSessionProxy,
            controlSession);
        session.doWork();
        assertThat(session.isDone(), is(true));
        verify(controlSessionProxy).sendResponse(eq(correlationId), any(), any(), eq(controlPublication));
        verify(controlSessionProxy, never()).sendDescriptor(eq(correlationId), any(), eq(controlPublication));
    }
}