/*
 * Copyright 2012 LMAX Ltd.
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
package com.lmax.disruptor;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static java.util.Arrays.copyOf;

/**
 * Provides static methods for managing a {@link SequenceGroup} object.
 */
class SequenceGroups
{
    static <T> void addSequences(
        final T holder,
        final AtomicReferenceFieldUpdater<T, Sequence[]> updater,
        final Cursored cursor,
        final Sequence... sequencesToAdd)
    {
        long cursorSequence;
        Sequence[] updatedSequences;
        Sequence[] currentSequences;

        do
        {
            currentSequences = updater.get(holder);
            updatedSequences = copyOf(currentSequences, currentSequences.length + sequencesToAdd.length);
            cursorSequence = cursor.getCursor();

            int index = currentSequences.length;
            for (Sequence sequence : sequencesToAdd)
            {
                // 新加的gatingSequence都初始化为cursorSequence, 作为新加的消费者的起始点
                sequence.set(cursorSequence);
                updatedSequences[index++] = sequence;
            }
        }
        while (!updater.compareAndSet(holder, currentSequences, updatedSequences));

        // 优化措施,尽量减少新加进来的consumer对producer或者其它consumer的block
        cursorSequence = cursor.getCursor();
        for (Sequence sequence : sequencesToAdd)
        {
            sequence.set(cursorSequence);
        }
    }

    static <T> boolean removeSequence(
        final T holder,
        final AtomicReferenceFieldUpdater<T, Sequence[]> sequenceUpdater,
        final Sequence sequence)
    {
        int numToRemove;
        Sequence[] oldSequences;
        Sequence[] newSequences;

        do
        {
            oldSequences = sequenceUpdater.get(holder);

            numToRemove = countMatching(oldSequences, sequence);

            if (0 == numToRemove)
            {
                break;
            }

            final int oldSize = oldSequences.length;
            newSequences = new Sequence[oldSize - numToRemove];

            for (int i = 0, pos = 0; i < oldSize; i++)
            {
                final Sequence testSequence = oldSequences[i];
                if (sequence != testSequence)
                {
                    newSequences[pos++] = testSequence;
                }
            }
        }
        while (!sequenceUpdater.compareAndSet(holder, oldSequences, newSequences));

        return numToRemove != 0;
    }

    private static <T> int countMatching(T[] values, final T toMatch)
    {
        int numToRemove = 0;
        for (T value : values)
        {
            if (value == toMatch) // Specifically uses identity
            {
                numToRemove++;
            }
        }
        return numToRemove;
    }
}
