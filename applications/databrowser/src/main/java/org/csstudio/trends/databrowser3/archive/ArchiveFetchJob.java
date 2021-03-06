/*******************************************************************************
 * Copyright (c) 2010-2018 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.csstudio.trends.databrowser3.archive;

import static org.csstudio.trends.databrowser3.Activator.logger;

import java.text.MessageFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import org.csstudio.trends.databrowser3.Activator;
import org.csstudio.trends.databrowser3.Messages;
import org.csstudio.trends.databrowser3.model.ArchiveDataSource;
import org.csstudio.trends.databrowser3.model.PVItem;
import org.csstudio.trends.databrowser3.model.RequestType;
import org.csstudio.trends.databrowser3.preferences.Preferences;
import org.phoebus.archive.reader.ArchiveReader;
import org.phoebus.archive.reader.ArchiveReaders;
import org.phoebus.archive.reader.UnknownChannelException;
import org.phoebus.archive.reader.ValueIterator;
import org.phoebus.framework.jobs.Job;
import org.phoebus.framework.jobs.JobManager;
import org.phoebus.framework.jobs.JobMonitor;
import org.phoebus.framework.jobs.JobRunnable;
import org.phoebus.util.time.TimestampFormats;
import org.phoebus.vtype.VType;

/** JobRunnable for fetching archived data.
 *
 *  <p>Actually spawns another thread so that the 'main' job can
 *  poll the progress monitor for cancellation and ask the secondary
 *  thread to cancel.
 *  @author Kay Kasemir
 */
@SuppressWarnings("nls")
public class ArchiveFetchJob implements JobRunnable
{
    /** Poll period in millisecs */
    private static final int POLL_PERIOD_MS = 1000;

    /** Item for which to fetch samples */
    private final PVItem item;

    /** Start/End time */
    private final Instant start, end;

    /** Listener that's notified when (if) we completed OK */
    private final ArchiveFetchJobListener listener;

    private Job job;

    /** Thread that performs the actual background work.
     *
     *  Instead of directly accessing the archive, ArchiveFetchJob launches
     *  a WorkerThread for the actual archive access, so that the Job
     *  can then poll the progress monitor for cancellation and if
     *  necessary interrupt the WorkerThread which might be 'stuck'
     *  in a long running operation.
     */
    class WorkerThread implements Runnable
    {
        private volatile String message = "";
        private volatile boolean cancelled = false;

        /** Archive reader that's currently queried */
        private AtomicReference<ArchiveReader> reader = new AtomicReference<>();

        /** @return Message that somehow indicates progress */
        public String getMessage()
        {
            return message;
        }

        /** Request thread to cancel its operation */
        public void cancel()
        {
            cancelled = true;

            final ArchiveReader the_reader = reader.get();
            if (the_reader != null)
                the_reader.cancel();
        }

        /** {@inheritDoc} */
        @Override
        public void run()
        {
            logger.log(Level.FINE, "Starting {0}", ArchiveFetchJob.this);
            final long start_time = System.currentTimeMillis();
            long samples = 0;
            final int bins = Preferences.plot_bins;
            final Collection<ArchiveDataSource> archives = item.getArchiveDataSources();
            final List<ArchiveDataSource> archives_without_channel = new ArrayList<>();
            int i = 0;
            for (ArchiveDataSource archive : archives)
            {
                if (cancelled)
                    break;
                final String url = archive.getUrl();
                // Display "N/total", using '1' for the first sub-archive.
                message = MessageFormat.format(Messages.ArchiveFetchDetailFmt,
                                               archive.getName(), ++i, archives.size());
                try
                (
                    final ArchiveReader the_reader = ArchiveReaders.createReader(url);
                )
                {
                    reader.set(the_reader);
                    final ValueIterator value_iter;
                    try
                    {
                        if (item.getRequestType() == RequestType.RAW)
                            value_iter = the_reader.getRawValues(item.getResolvedName(), start, end);
                        else
                            value_iter = the_reader.getOptimizedValues(item.getResolvedName(), start, end, bins);
                    }
                    catch (UnknownChannelException e)
                    {
                        // Do not immediately notify about unknown channels. First search for the data in all archive
                        // sources and only report this kind of errors at the end
                        archives_without_channel.add(archive);
                        continue;
                    }
                    // Get samples into array
                    final List<VType> result = new ArrayList<>();
                    while (value_iter.hasNext())
                        result.add(value_iter.next());
                    value_iter.close();
                    samples += result.size();
                    item.mergeArchivedSamples(archive.getName(), result);
                    if (cancelled)
                        break;
                }
                catch (Exception ex)
                {   // Tell listener unless it's the result of a 'cancel'?
                    if (! cancelled)
                        listener.archiveFetchFailed(ArchiveFetchJob.this, archive, ex);
                    // Continue with the next data source
                }
            }
            reader.set(null);
            final long end_time = System.currentTimeMillis();
            logger.log(Level.FINE,
                    "Ended {0} with {1} samples in {2} secs",
                    new Object[] { ArchiveFetchJob.this, samples, (end_time - start_time)/1000 });

            if (cancelled)
                return;

            if (archives_without_channel.size() > 0)
                listener.channelNotFound(ArchiveFetchJob.this,
                        archives_without_channel.size() < archives.size(),
                        archives_without_channel);

            listener.fetchCompleted(ArchiveFetchJob.this);
        }

        @Override
        public String toString()
        {
            return "WorkerTread for " + ArchiveFetchJob.this.toString();
        }
    }

    /** Schedule a new job.
     *
     *  @param item the item for which the data are fetched
     *  @param start the lower time boundary for the historic data
     *  @param end the upper time boundary for the history data
     *  @param listener the listener notified when the job is complete or an error happens
     */
    public ArchiveFetchJob(final PVItem item, final Instant start,
                           final Instant end,
                           final ArchiveFetchJobListener listener)
    {
        this.item = item;
        this.start = start;
        this.end = end;
        this.listener = listener;
        this.job = JobManager.schedule(toString(), this);
    }

    /** @return PVItem for which this job was created */
    public PVItem getPVItem()
    {
        return item;
    }

    /** Job's main routine which starts and monitors WorkerThread */
    @Override
    public void run(JobMonitor monitor) throws Exception
    {
        if (item == null)
            return;

        monitor.beginTask(Messages.ArchiveFetchStart);
        final WorkerThread worker = new WorkerThread();
        final Future<?> done = Activator.thread_pool.submit(worker);
        // Poll worker and progress monitor
        long start = System.currentTimeMillis();
        while (!done.isDone())
        {   // Wait until worker is done, or time out to update info message
            try
            {
                done.get(POLL_PERIOD_MS, TimeUnit.MILLISECONDS);
            }
            catch (Exception ex)
            {
                // Ignore
            }
            final long seconds = (System.currentTimeMillis() - start) / 1000;
            final String info = MessageFormat.format(Messages.ArchiveFetchProgressFmt,
                                                     worker.getMessage(), seconds);
            monitor.updateTaskName(info);
            // Try to cancel the worker in response to user's cancel request.
            // Continues to cancel the worker until isDone()
            if (monitor.isCanceled())
                worker.cancel();
        }
    }

    /** Programmatically cancel job
     *
     *  <p>.. because a new job for the same item
     *  replaces existing one.
     *  In addition, job may be cancelled by user
     *  from job UI.
     */
    public void cancel()
    {
        job.cancel();
    }

    /** @return Debug string */
    @Override
    public String toString()
    {
        return MessageFormat.format(Messages.ArchiveFetchJobFmt,
            item.getResolvedDisplayName(),
            TimestampFormats.FULL_FORMAT.format(start),
            TimestampFormats.FULL_FORMAT.format(end));
    }
}
