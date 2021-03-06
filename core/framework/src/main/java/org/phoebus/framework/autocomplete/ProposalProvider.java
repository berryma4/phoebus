/*******************************************************************************
 * Copyright (c) 2017 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.phoebus.framework.autocomplete;

import java.util.List;

/** Provider of {@link Proposal}s
 *
 *  @author Kay Kasemir
 */
public interface ProposalProvider
{
    /** @return Name of this provider */
    public String getName();

    /** Get proposals
     *
     *  @param text Text entered by user
     *  @return {@link Proposal}s that could be applied to the text
     */
    public List<Proposal> lookup(String text);
}
