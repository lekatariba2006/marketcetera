package org.marketcetera.core;

/**
 * Our top-level error that is thrown when things really break
 * @author Toli Kuznets
 * @version $Id$
 */
@ClassVersion("$Id$")
public class PanicError extends Error
{
    public PanicError(String message)
    {
        super(message);
    }

    public PanicError(Throwable cause)
    {
        super(cause);
    }

    public PanicError(String message, Throwable cause)
    {
        super(message, cause);
    }
    private static final long serialVersionUID = -403949921663251427L;
}
