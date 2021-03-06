package org.marketcetera.photon.views;


import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.databinding.AggregateValidationStatus;
import org.eclipse.core.databinding.Binding;
import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.ObservablesManager;
import org.eclipse.core.databinding.UpdateValueStrategy;
import org.eclipse.core.databinding.ValidationStatusProvider;
import org.eclipse.core.databinding.beans.BeansObservables;
import org.eclipse.core.databinding.conversion.Converter;
import org.eclipse.core.databinding.conversion.NumberToStringConverter;
import org.eclipse.core.databinding.observable.IObservable;
import org.eclipse.core.databinding.observable.list.IListChangeListener;
import org.eclipse.core.databinding.observable.list.IObservableList;
import org.eclipse.core.databinding.observable.list.ListChangeEvent;
import org.eclipse.core.databinding.observable.list.ListDiffEntry;
import org.eclipse.core.databinding.observable.value.ComputedValue;
import org.eclipse.core.databinding.observable.value.DecoratingObservableValue;
import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.core.databinding.observable.value.IValueChangeListener;
import org.eclipse.core.databinding.observable.value.ValueChangeEvent;
import org.eclipse.core.databinding.validation.ValidationStatus;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.databinding.swt.SWTObservables;
import org.eclipse.jface.databinding.viewers.ObservableListContentProvider;
import org.eclipse.jface.databinding.viewers.ObservableMapLabelProvider;
import org.eclipse.jface.databinding.viewers.ViewersObservables;
import org.eclipse.jface.fieldassist.FieldDecorationRegistry;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.forms.widgets.ScrolledForm;
import org.marketcetera.algo.BrokerAlgo;
import org.marketcetera.core.CoreException;
import org.marketcetera.photon.BrokerManager;
import org.marketcetera.photon.BrokerManager.Broker;
import org.marketcetera.photon.BrokerManager.BrokerLabelProvider;
import org.marketcetera.photon.PhotonPlugin;
import org.marketcetera.photon.commons.databinding.TypedConverter;
import org.marketcetera.photon.commons.databinding.TypedObservableValue;
import org.marketcetera.photon.commons.ui.databinding.RequiredFieldSupport;
import org.marketcetera.photon.commons.ui.databinding.UpdateStrategyFactory;
import org.marketcetera.photon.marketdata.IMarketDataManager;
import org.marketcetera.photon.marketdata.IMarketDataReference;
import org.marketcetera.photon.model.marketdata.MDTopOfBook;
import org.marketcetera.photon.ui.databinding.StatusToImageConverter;
import org.marketcetera.photon.views.providers.AlgoTableColumnEditorSupport;
import org.marketcetera.photon.views.providers.AlgoTableObservableMapLabelProvider;
import org.marketcetera.trade.BrokerID;
import org.marketcetera.trade.Instrument;
import org.marketcetera.trade.NewOrReplaceOrder;
import org.marketcetera.trade.OrderReplace;
import org.marketcetera.trade.OrderSingle;
import org.marketcetera.util.log.SLF4JLoggerProxy;
import org.marketcetera.util.misc.ClassVersion;

import com.ibm.icu.text.NumberFormat;

/* $License$ */

/**
 * This is the abstract base class for all order ticket views. It is responsible
 * for setting up the databindings for the "common" order ticket fields, such as
 * side, price, and time in force.
 * 
 * It also is responsible for managing the "custom fields" for order messages
 * that can be set by the user in the preferences dialog, and activated in the
 * order ticket.
 * 
 * @author gmiller
 * @author <a href="mailto:will@marketcetera.com">Will Horn</a>
 * @since 0.6.0
 */
@ClassVersion("$Id$")
public abstract class OrderTicketView<M extends OrderTicketModel, T extends IOrderTicket>
        extends XSWTView<T> {

    private static final String CUSTOM_FIELD_VIEW_SAVED_STATE_KEY_PREFIX = "CUSTOM_FIELD_CHECKED_STATE_OF_"; //$NON-NLS-1$

    private final Class<T> mTicketClass;

    private final ObservablesManager mObservablesManager = new ObservablesManager();

    private final M mModel;

    private IMemento mMemento;

    private ComboViewer mAvailableBrokersViewer;

    private CheckboxTableViewer mCustomFieldsTableViewer;
    private ComboViewer mAvailableAlgosViewer;
    private TableViewer mAlgoTagsTableViewer;
    private ComboViewer mSideComboViewer;

    private ComboViewer mTimeInForceComboViewer;

    private ComboViewer mOrderTypeComboViewer;
    private IValueChangeListener mFocusListener;
    /**
     * used to schedule updates to the price value
     */
    private ScheduledExecutorService priceUpdateService = Executors.newSingleThreadScheduledExecutor();
    /**
     * holds the current price update job token
     */
    private volatile ScheduledFuture<?> priceUpdateFuture;
    /**
     * provides current top-of-book values
     */
    private volatile MDTopOfBook topOfBook;
    /**
     * reference to top-of-book
     */
    private volatile IMarketDataReference<MDTopOfBook> topOfBookReference;
    /**
     * Constructor.
     * 
     * @param ticketClass
     *            type of ticket class
     * @param model
     *            the ticket model
     */
    protected OrderTicketView(Class<T> ticketClass, M model) {
        mTicketClass = ticketClass;
        mModel = model;
    }

    @Override
    public void init(IViewSite site, IMemento memento) throws PartInitException {
        super.init(site, memento);
        mMemento = memento;
    }

    @Override
    protected Class<T> getXSWTInterfaceClass() {
        return mTicketClass;
    }

    /**
     * Returns the view model.
     * 
     * @return the view model
     */
    protected M getModel() {
        return mModel;
    }
    /**
     * Returns the {@link ObservablesManager} that will clean up managed
     * observables.
     * 
     * @return the observables manager
     */
    public ObservablesManager getObservablesManager() {
        return mObservablesManager;
    }

    @Override
    protected void finishUI() {
        final T ticket = getXSWTView();

        /*
         * Set background of error message area.
         */
        Color bg = ticket.getForm().getParent().getBackground();
        ticket.getErrorIconLabel().setBackground(bg);
        ticket.getErrorMessageLabel().setBackground(bg);

        /*
         * Set up viewers.
         */
        initViewers(ticket);

        /*
         * Additional widget customizations.
         */
        customizeWidgets(ticket);

        /*
         * Handle clear button click.
         */
        ticket.getClearButton().addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                getModel().clearOrderMessage();
            }
        });
        ticket.getPegToMidpoint().addSelectionListener(new SelectionAdapter() {
            /* (non-Javadoc)
             * @see org.eclipse.swt.events.SelectionAdapter#widgetSelected(org.eclipse.swt.events.SelectionEvent)
             */
            @Override
            public void widgetSelected(SelectionEvent inE)
            {
                Button pegToMidpoint = ticket.getPegToMidpoint();
                pegToMidpoint.setSelection(pegToMidpoint(pegToMidpoint.getSelection()));
                if(!pegToMidpoint.getSelection()) {
                    ticket.getPegToMidpointLocked().setSelection(false);
                }
                updateOrderPegToMidpoint();
            }
        });
        ticket.getPegToMidpointLocked().addSelectionListener(new SelectionAdapter() {
            /* (non-Javadoc)
             * @see org.eclipse.swt.events.SelectionAdapter#widgetSelected(org.eclipse.swt.events.SelectionEvent)
             */
            @Override
            public void widgetSelected(SelectionEvent inE)
            {
                if(ticket.getPegToMidpointLocked().getSelection()) {
                    if(ticket.getPegToMidpoint().getSelection()) {
                        updateOrderPegToMidpoint();
                    } else {
                        ticket.getPegToMidpointLocked().setSelection(false);
                    }
                } else {
                    updateOrderPegToMidpoint();
                }
            }
        });
        ticket.getSymbolText().addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent inEvent)
            {
                pegToMidpoint(false);
                String value = StringUtils.trimToNull(ticket.getSymbolText().getText());
                ticket.getPegToMidpoint().setEnabled(value != null);
                ticket.getPegToMidpointLocked().setEnabled(ticket.getPegToMidpoint().getEnabled());
                ticket.getPegToMidpoint().setSelection(false);
                ticket.getPriceText().setText("");
            }
        });
        /*
         * Handle send button click.
         */
        ticket.getSendButton().addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                handleSend();
            }
        });

        /*
         * Bind to model.
         */
        try {
            bindFormTitle();
            bindMessage();
            bindCustomFields();
            bindAlgoTags();
        } catch (Exception e) {
            PhotonPlugin.getMainConsoleLogger().error(
                    Messages.ORDER_TICKET_VIEW_CANNOT_BIND_TO_TICKET.getText(),
                    e);
        }

        /*
         * Initialize validation (error message area).
         */
        initValidation();

        /*
         * Control focus when the model's order changes.
         */
        mFocusListener = new IValueChangeListener() {
            @Override
            public void handleValueChange(ValueChangeEvent event) {
                setFocus();
            }
        };
        getModel().getOrderObservable().addValueChangeListener(mFocusListener);

        ticket.getForm().reflow(true);
    }
    /**
     * Update the underlying order peg-to-midpoint value.
     */
    private void updateOrderPegToMidpoint()
    {
        T ticket = getXSWTView();
        Button pegToMidpoint = ticket.getPegToMidpoint();
        Button pegToMidpointLocked = ticket.getPegToMidpointLocked();
        boolean pegToMidpointOrderValue = pegToMidpoint.getSelection();
        // if the end result is that peg-to-midpoint is selected, set it on the underlying order, but only if 'locked' is _not_ selected
        if(pegToMidpointOrderValue) {
            pegToMidpointOrderValue = !pegToMidpointLocked.getSelection();
        }
        getModel().getOrderObservable().getTypedValue().setPegToMidpoint(pegToMidpointOrderValue);
    }
    /**
     * Update the price value with the top-of-book midpoint value.
     */
    private void updateTopOfBook()
    {
        if(topOfBook != null) {
            T ticket = getXSWTView();
            if(ticket.getPriceText().getEnabled()) {
                BigDecimal askPrice = topOfBook.getAskPrice();
                BigDecimal bidPrice = topOfBook.getBidPrice();
                if(!ticket.getPegToMidpointLocked().getSelection() && askPrice != null && bidPrice != null) {
                    BigDecimal midPoint = askPrice.add(bidPrice);
                    midPoint = midPoint.divide(new BigDecimal(2)).setScale(2,RoundingMode.HALF_UP);
                    ticket.getPriceText().setText(midPoint.toPlainString());
                }
            } else {
                ticket.getPriceText().setText("");
            }
        }
    }
    /**
     * Activate or deactivate peg-to-midpoint feature.
     *
     * @param inActivate a <code>boolean</code> value
     * @return a <code>boolean</code> value indicating whether the feature was successfully activated or not
     */
    private boolean pegToMidpoint(boolean inActivate)
    {
        if(inActivate) {
            IMarketDataManager marketDataManager = PhotonPlugin.getDefault().getMarketDataManager();
            if(marketDataManager.isRunning()) {
                Instrument instrument = getModel().getOrderObservable().getTypedValue().getInstrument();
                if(instrument == null) {
                    return false;
                } else {
                    topOfBookReference = marketDataManager.getMarketData().getTopOfBook(instrument);
                    topOfBook = topOfBookReference.get();
                    // TODO need to hook up some kind of listener here to changes in top of book
                    priceUpdateFuture = priceUpdateService.scheduleAtFixedRate(new Runnable() {
                        @Override
                        public void run()
                        {
                            try {
                                Display display = Display.getDefault();
                                display.asyncExec(new Runnable() {
                                    @Override
                                    public void run()
                                    {
                                        try {
                                            updateTopOfBook();
                                        } catch (Exception e) {
                                            SLF4JLoggerProxy.warn(PhotonPlugin.MAIN_CONSOLE_LOGGER_NAME,
                                                                  e);
                                        }
                                    }
                                });
                            } catch (Exception e) {
                                SLF4JLoggerProxy.warn(PhotonPlugin.MAIN_CONSOLE_LOGGER_NAME,
                                                      e);
                            }
                        }
                    },1000,1000,TimeUnit.MILLISECONDS);
                    return true;
                }
            } else {
                SLF4JLoggerProxy.warn(PhotonPlugin.MAIN_CONSOLE_LOGGER_NAME,
                                      "Market data is not available");
                return false;
            }
        } else {
            if(priceUpdateFuture != null) {
                priceUpdateFuture.cancel(true);
                priceUpdateFuture = null;
            }
            if(topOfBookReference != null) {
                topOfBookReference.dispose();
                topOfBookReference = null;
                topOfBook = null;
            }
            getModel().getOrderObservable().getTypedValue().setPegToMidpoint(false);
            return false;
        }
    }
    /**
     * Customize the widgets.
     * 
     * @param ticket
     *            the order ticket.
     */
    protected void customizeWidgets(T ticket) {
        /*
         * Update size of text fields since default will be small.
         */
        updateSize(ticket.getQuantityText(), 10);
        updateSize(ticket.getSymbolText(), 10);
        updateSize(ticket.getPriceText(), 10);
        updateSize(ticket.getAccountText(), 10);

        /*
         * Customize text fields to auto select the text on focus to make it
         * easy to change the value.
         */
        selectOnFocus(ticket.getQuantityText());
        selectOnFocus(ticket.getSymbolText());
        selectOnFocus(ticket.getPriceText());
        selectOnFocus(ticket.getAccountText());
        selectOnFocus(ticket.getExecutionDestinationText());
        selectOnFocus(ticket.getDisplayQuantityText());

        /*
         * If the ticket has no errors, enter on these fields will trigger a
         * send.
         */
        addSendOrderListener(ticket.getSideCombo());
        addSendOrderListener(ticket.getQuantityText());
        addSendOrderListener(ticket.getDisplayQuantityText());
        addSendOrderListener(ticket.getSymbolText());
        addSendOrderListener(ticket.getOrderTypeCombo());
        addSendOrderListener(ticket.getPriceText());
        addSendOrderListener(ticket.getBrokerCombo());
        addSendOrderListener(ticket.getTifCombo());
        addSendOrderListener(ticket.getAccountText());
    }
    /**
     * Set up viewers.
     * 
     * @param ticket
     */
    protected void initViewers(final T ticket) {
        /*
         * Side combo based on Side enum.
         */
        mSideComboViewer = new ComboViewer(ticket.getSideCombo());
        mSideComboViewer.setContentProvider(new ArrayContentProvider());
        mSideComboViewer.setInput(getModel().getValidSideValues());

        /*
         * Order type combo based on OrderType enum.
         */
        mOrderTypeComboViewer = new ComboViewer(ticket.getOrderTypeCombo());
        mOrderTypeComboViewer.setContentProvider(new ArrayContentProvider());
        mOrderTypeComboViewer.setInput(getModel().getValidOrderTypeValues());
        /*
         * Broker combo based on available brokers.
         */
        mAvailableBrokersViewer = new ComboViewer(ticket.getBrokerCombo());
        mAvailableBrokersViewer.setContentProvider(new ObservableListContentProvider());
        mAvailableBrokersViewer.setLabelProvider(new BrokerLabelProvider());
        mAvailableBrokersViewer.setInput(getModel().getValidBrokers());
        // watches broker combo and sets currently selected broker appropriately
        mAvailableBrokersViewer.addSelectionChangedListener(new ISelectionChangedListener() {
            @Override
            public void selectionChanged(SelectionChangedEvent inEvent)
            {
                Broker broker = (Broker)((StructuredSelection)inEvent.getSelection()).getFirstElement();
                if(broker == null || BrokerManager.AUTO_SELECT_BROKER.getName().equals(broker.getName())) {
                    getModel().setSelectedBroker(BrokerManager.AUTO_SELECT_BROKER);
                    getModel().setSelectedAlgo(null);
                } else {
                    getModel().setSelectedBroker(BrokerManager.getCurrent().getBroker(broker.getId()));
                }
            }
        });
        /*
         * broker algos based on selected broker
         */
        mAvailableAlgosViewer = new ComboViewer(ticket.getAlgoCombo());
        mAvailableAlgosViewer.setContentProvider(new ObservableListContentProvider());
        mAvailableAlgosViewer.setLabelProvider(new AlgoLabelProvider());
        mAvailableAlgosViewer.setInput(getModel().getValidAlgos());
        mAvailableAlgosViewer.addSelectionChangedListener(new ISelectionChangedListener() {
            @Override
            public void selectionChanged(SelectionChangedEvent inEvent)
            {
                Object selectedObject = ((StructuredSelection)inEvent.getSelection()).getFirstElement();
                BrokerAlgo brokerAlgo = null;
                if(selectedObject != null && selectedObject instanceof BrokerAlgo) {
                    brokerAlgo = (BrokerAlgo)selectedObject;
                }
                getModel().setSelectedAlgo(brokerAlgo);
            }
        });
        /*
         * Time in Force combo based on TimeInForce enum.
         * 
         * An extra blank entry is added since the field is optional.
         */
        mTimeInForceComboViewer = new ComboViewer(ticket.getTifCombo());
        mTimeInForceComboViewer.setContentProvider(new ArrayContentProvider());
        mTimeInForceComboViewer.setInput(getModel().getValidTimeInForceValues());

        /*
         * Custom fields table.
         * 
         * Input is bound to model in bindCustomFields.
         */
        mCustomFieldsTableViewer = new CheckboxTableViewer(ticket.getCustomFieldsTable());
        ObservableListContentProvider contentProvider = new ObservableListContentProvider();
        mCustomFieldsTableViewer.setContentProvider(contentProvider);
        mCustomFieldsTableViewer.setLabelProvider(new ObservableMapLabelProvider(BeansObservables.observeMaps(contentProvider.getKnownElements(),
                                                                                                              CustomField.class,
                                                                                                              new String[] { "keyString", "valueString" })));//$NON-NLS-1$ //$NON-NLS-2$
        mAlgoTagsTableViewer = new TableViewer(ticket.getAlgoTagsTable());
        ObservableListContentProvider algoTagsContentProvider = new ObservableListContentProvider();
        TableViewerColumn valueColumn = new TableViewerColumn(mAlgoTagsTableViewer,
                                                              mAlgoTagsTableViewer.getTable().getColumns()[1]);
        mAlgoTagsTableViewer.setContentProvider(algoTagsContentProvider);
        mAlgoTagsTableViewer.setLabelProvider(new AlgoTableObservableMapLabelProvider(BeansObservables.observeMaps(algoTagsContentProvider.getKnownElements(),
                                                                                                                   ObservableAlgoTag.class,
                                                                                                                   new String[] { "keyString", "valueString", "descriptionString" })));//$NON-NLS-1$ //$NON-NLS-2$
        valueColumn.setEditingSupport(new AlgoTableColumnEditorSupport(mAlgoTagsTableViewer));
        // disable the peg to midpoint until symbol has a value and order is established as non-market
        ticket.getPegToMidpoint().setEnabled(false);
        ticket.getPegToMidpointLocked().setEnabled(ticket.getPegToMidpoint().getEnabled());
    }
    /**
     * Get the UI string to show for a "new order" message.
     * 
     * @return the UI string
     */
    protected abstract String getNewOrderString();

    /**
     * Get the UI string to show for a "replace" message.
     * 
     * @return the UI string
     */
    protected abstract String getReplaceOrderString();

    /**
     * Bind the top level form title to show different text depending on the
     * order type.
     */
    protected void bindFormTitle() {
        TypedObservableValue<String> formTextObservable = new TypedObservableValue<String>(
                String.class) {
            @Override
            protected String doGetValue() {
                return getXSWTView().getForm().getText();
            }

            @Override
            protected void doSetTypedValue(String value) {
                getXSWTView().getForm().setText(value);
            }
        };
        getObservablesManager().addObservable(formTextObservable);
        getDataBindingContext().bindValue(
                formTextObservable,
                getModel().getOrderObservable(),
                null,
                new UpdateValueStrategy().setConverter(new Converter(
                        NewOrReplaceOrder.class, String.class) {
                    public Object convert(Object fromObject) {
                        if (fromObject instanceof OrderReplace) {
                            return getReplaceOrderString();
                        } else if (fromObject instanceof OrderSingle) {
                            return getNewOrderString();
                        } else {
                            return null;
                        }
                    }
                }));
    }

    /**
     * Binds the UI to the model.
     */
    protected void bindMessage() {
        final DataBindingContext dbc = getDataBindingContext();
        final OrderTicketModel model = getModel();
        final IOrderTicket ticket = getXSWTView();

        /*
         * Side
         */
        bindRequiredCombo(mSideComboViewer, model.getSide(),
                Messages.ORDER_TICKET_VIEW_SIDE__LABEL.getText());
        enableForNewOrderOnly(mSideComboViewer.getControl());

        /*
         * Quantity
         */
        bindRequiredDecimal(ticket.getQuantityText(), model.getQuantity(),
                Messages.ORDER_TICKET_VIEW_QUANTITY__LABEL.getText());
        
        
        /*
         * Display Quantity
         */
        bindText(ticket.getDisplayQuantityText(), model.getDisplayQuantity());
        /*
         * Symbol
         */
        bindRequiredText(ticket.getSymbolText(), 
                         getModel().getSymbol(),
                         Messages.ORDER_TICKET_VIEW_SYMBOL__LABEL.getText());
        enableForNewOrderOnly(ticket.getSymbolText());

        /*
         * Order Type
         */
        bindRequiredCombo(mOrderTypeComboViewer, model.getOrderType(),
                Messages.ORDER_TICKET_VIEW_ORDER_TYPE__LABEL.getText());

        /*
         * Price
         * 
         * Need custom required field logic since price is only required for
         * limit orders.
         */
        {
            Binding binding = bindDecimal(ticket.getPriceText(), model
                    .getPrice(), Messages.ORDER_TICKET_VIEW_PRICE__LABEL
                    .getText());
            /*
             * RequiredFieldSupport reports an error if the value is null or
             * empty string. We want this behavior when the order is a limit
             * order, but not when it is a market order (since empty string is
             * correct as the price is uneditable. So we decorate the observable
             * and pass the decorated one to RequiredFieldsupport.
             */
            IObservableValue priceDecorator = new DecoratingObservableValue(
                    (IObservableValue) binding.getTarget(), false) {
                @Override
                public Object getValue() {
                    Object actualValue = super.getValue();
                    if ("".equals(actualValue) //$NON-NLS-1$
                            && !model.isLimitOrder().getTypedValue()) {
                        /*
                         * Return an object to "trick" RequiredFieldSupport to
                         * not error.
                         */
                        return new Object();
                    }
                    return actualValue;
                }
            };
            RequiredFieldSupport.initFor(dbc, priceDecorator,
                    Messages.ORDER_TICKET_VIEW_PRICE__LABEL.getText(), false,
                    SWT.BOTTOM | SWT.LEFT, binding);
            dbc.bindValue(SWTObservables.observeEnabled(ticket.getPriceText()),
                    model.isLimitOrder());
        }

        /*
         * Broker
         * 
         * Custom binding logic required since the viewer list can dynamically
         * change.
         */
        {
            IObservableValue target = ViewersObservables
                    .observeSingleSelection(mAvailableBrokersViewer);
            /*
             * Bind the target (combo) to the model, but use POLICY_ON_REQUEST
             * for target-to-model binding since we don't want the model to
             * change simply because a broker went down. The target-to-model
             * updates are handled manually below.
             */
            final Binding binding = dbc.bindValue(target, model.getBrokerId(),
                    new UpdateValueStrategy(
                            UpdateValueStrategy.POLICY_ON_REQUEST)
                            .setConverter(new TypedConverter<Broker, BrokerID>(
                                    Broker.class, BrokerID.class) {
                                @Override
                                public BrokerID doConvert(Broker fromObject) {
                                    return fromObject.getId();
                                }
                            }), new UpdateValueStrategy()
                            .setConverter(new TypedConverter<BrokerID, Broker>(
                                    BrokerID.class, Broker.class) {
                                @Override
                                public Broker doConvert(BrokerID fromObject) {
                                    return BrokerManager.getCurrent()
                                            .getBroker(fromObject);
                                }
                            }));
            /*
             * If the target changes and the new value is not null, then this
             * was a user selection and the model should be updated.
             */
            target.addValueChangeListener(new IValueChangeListener() {
                @Override
                public void handleValueChange(ValueChangeEvent event) {
                    if (event.diff.getNewValue() != null) {
                        binding.updateTargetToModel();
                    }
                }
            });
            /*
             * When the broker list changes, we force a model-to-target update
             * to ensure the two are in sync if possible.
             */
            final IListChangeListener listener = new IListChangeListener() {
                @Override
                public void handleListChange(ListChangeEvent event) {
                    binding.updateModelToTarget();
                }
            };
            BrokerManager.getCurrent().getAvailableBrokers()
                    .addListChangeListener(listener);
            /*
             * Need to remove the listener when the widget is disposed.
             */
            mAvailableBrokersViewer.getControl().addDisposeListener(
                    new DisposeListener() {
                        @Override
                        public void widgetDisposed(DisposeEvent e) {
                            BrokerManager.getCurrent().getAvailableBrokers()
                                    .removeListChangeListener(listener);
                        }
                    });
            /*
             * If the model has a broker id, but the target doesn't have a
             * corresponding entry, the target will be null which needs to
             * generate an error.
             */
            setRequired(binding, Messages.ORDER_TICKET_VIEW_BROKER__LABEL
                    .getText());
        }
        enableForNewOrderOnly(mAvailableBrokersViewer.getControl());
        /*
         * broker algos
         */
        bindCombo(mAvailableAlgosViewer,
                  model.getBrokerAlgo());
        /*
         * Time in Force
         */
        bindCombo(mTimeInForceComboViewer, model.getTimeInForce());

        /*
         * Account
         */
        bindText(getXSWTView().getAccountText(), model.getAccount());
        /*
         * execution destination
         */
        bindText(getXSWTView().getExecutionDestinationText(),
                 model.getExecutionDestination());
    }
    /**
     * Listener for changes on element from algo tags list.
     */
    private PropertyChangeListener algoTagsListChanged = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent inEvent)
        {
            ObservableAlgoTag tag = (ObservableAlgoTag)inEvent.getSource();
            int index = getModel().getAlgoTagsList().indexOf(tag);
            getModel().getAlgoTagsList().set(index,
                                             tag);
        }
    };
    /**
     * Binds the algo tags on the model to the view.
     */
    protected void bindAlgoTags()
    {
        final M model = getModel();
        mAlgoTagsTableViewer.setInput(model.getAlgoTagsList());
        model.getAlgoTagsList().addListChangeListener(new IListChangeListener() {
            public void handleListChange(ListChangeEvent event)
            {
                for(ListDiffEntry entry:event.diff.getDifferences()){
                    if(entry.isAddition()){
                        ObservableAlgoTag tag = (ObservableAlgoTag)entry.getElement();
                        tag.addPropertyChangeListener(algoTagsListChanged);
                    } else {
                        ObservableAlgoTag tag = (ObservableAlgoTag)entry.getElement();
                        tag.removePropertyChangeListener(algoTagsListChanged);
                    }
                }
            }
        });
        //Add validation for algo tags list
        getDataBindingContext().addValidationStatusProvider(new ValidationStatusProvider() {
            @Override
            public IObservableValue getValidationStatus() {
                return new ComputedValue() {
                    @Override
                    protected Object calculate() {
                        for(Object object: getModel().getAlgoTagsList()){
                            ObservableAlgoTag algoTag = (ObservableAlgoTag)object;
                            try {
                                algoTag.validate();
                            } catch (CoreException e) {
                                return ValidationStatus.error(e.getLocalizedMessage());
                            }
                        }
                        return ValidationStatus.OK_STATUS;
                    }
                };
            }
            @Override
            public IObservableList getTargets() {
                return ViewersObservables.observeMultiSelection(mAlgoTagsTableViewer);
            }
            @Override
            public IObservableList getModels() {
                return getModel().getAlgoTagsList();
            }
        });
    }
    /**
     * Bind the custom fields on the model to the view.
     */
    protected void bindCustomFields() {
        M model = getModel();
        mCustomFieldsTableViewer.setInput(model.getCustomFieldsList());
        mCustomFieldsTableViewer
                .addCheckStateListener(new ICheckStateListener() {
                    public void checkStateChanged(CheckStateChangedEvent event) {
                        Object source = event.getElement();
                        ((CustomField) source).setEnabled(event.getChecked());
                    }
                });
        model.getCustomFieldsList().addListChangeListener(
                new IListChangeListener() {
                    public void handleListChange(ListChangeEvent event) {
                        ScrolledForm theForm = getXSWTView().getForm();
                        if (!theForm.isDisposed()) {
                            ListDiffEntry[] differences = event.diff
                                    .getDifferences();
                            for (ListDiffEntry listDiffEntry : differences) {
                                if (listDiffEntry.isAddition()) {
                                    CustomField customField = (CustomField) listDiffEntry
                                            .getElement();
                                    String key = CUSTOM_FIELD_VIEW_SAVED_STATE_KEY_PREFIX
                                            + customField.getKeyString();
                                    IMemento theMemento = getMemento();
                                    if (theMemento != null
                                            && theMemento.getInteger(key) != null) {
                                        boolean itemChecked = (theMemento
                                                .getInteger(key).intValue() != 0);
                                        customField.setEnabled(itemChecked);
                                    }
                                }
                            }
                            theForm.reflow(true);
                        }
                    }
                });
    }

    /**
     * Initialization the validation (error message area) of the view.
     */
    protected void initValidation() {
        DataBindingContext dbc = getDataBindingContext();
        AggregateValidationStatus aggregateValidationStatus = new AggregateValidationStatus(
                dbc, AggregateValidationStatus.MAX_SEVERITY);

        dbc.bindValue(SWTObservables.observeText(getXSWTView()
                .getErrorMessageLabel()), aggregateValidationStatus);

        dbc.bindValue(SWTObservables.observeImage(getXSWTView()
                .getErrorIconLabel()), aggregateValidationStatus, null,
                new UpdateValueStrategy()
                        .setConverter(new StatusToImageConverter()));

        dbc.bindValue(SWTObservables.observeEnabled(getXSWTView()
                .getSendButton()), aggregateValidationStatus, null,
                new UpdateValueStrategy()
                        .setConverter(new TypedConverter<IStatus, Boolean>(
                                IStatus.class, Boolean.class) {
                            @Override
                            public Boolean doConvert(IStatus fromObject) {
                                return fromObject.getSeverity() < IStatus.ERROR;
                            }
                        }));
    }

    /**
     * Binds a combo viewer to a model field that is required.
     * 
     * @param viewer
     *            the viewer
     * @param model
     *            the model observable
     * @return the binding
     */
    protected Binding bindCombo(ComboViewer viewer, IObservableValue model) {
        DataBindingContext dbc = getDataBindingContext();
        IObservableValue target = ViewersObservables
                .observeSingleSelection(viewer);
        return dbc.bindValue(target, model, new UpdateValueStrategy()
                .setConverter(new Converter(target.getValueType(), model
                        .getValueType()) {
                    @Override
                    public Object convert(Object fromObject) {
                        return fromObject instanceof OrderTicketModel.NullSentinel ? null
                                : fromObject;
                    }
                }), null);
    }
    /**
     * Binds a combo viewer and makes it required.
     * 
     * @param viewer
     *            the viewer
     * @param model
     *            the model observable
     * @param description
     *            the description for error messages
     * @return the binding
     */
    protected Binding bindRequiredCombo(ComboViewer viewer,
            IObservableValue model, String description) {
        DataBindingContext dbc = getDataBindingContext();
        IObservableValue target = ViewersObservables
                .observeSingleSelection(viewer);
        Binding binding = dbc.bindValue(target, model);
        setRequired(binding, description);
        return binding;
    }

    /**
     * Binds a text widget to a BigDecimal value.
     * 
     * @param text
     *            the widget
     * @param model
     *            the model observable
     * @param description
     *            the description for error messages
     * @return the binding
     */
    protected Binding bindDecimal(Text text, IObservableValue model,
            String description) {
        DataBindingContext dbc = getDataBindingContext();
        IObservableValue target = SWTObservables.observeText(text, SWT.Modify);
        NumberFormat numberFormat = NumberFormat.getInstance();
        numberFormat.setGroupingUsed(false);
        numberFormat.setMaximumFractionDigits(6);
        return dbc.bindValue(target, model, UpdateStrategyFactory
                .withConvertErrorMessage(new UpdateValueStrategy(),
                        Messages.ORDER_TICKET_VIEW_NOT_DECIMAL_ERROR
                                .getText(description)),
                new UpdateValueStrategy().setConverter(NumberToStringConverter
                        .fromBigDecimal(numberFormat)));
    }

    /**
     * Binds a text widget to a BigDecimal value and makes it required.
     * 
     * @param text
     *            the widget
     * @param model
     *            the model observable
     * @param description
     *            the description for error messages
     * @return the binding
     */
    protected Binding bindRequiredDecimal(Text text, IObservableValue model,
            String description) {
        Binding binding = bindDecimal(text, model, description);
        setRequired(binding, description);
        return binding;
    }

    /**
     * Binds a text widget to the model.
     * 
     * @param text
     *            the widget
     * @param model
     *            the model observable
     * @return the binding
     */
    protected Binding bindText(Text text, IObservableValue model) {
        DataBindingContext dbc = getDataBindingContext();
        IObservableValue target = SWTObservables.observeText(text, SWT.Modify);
        UpdateValueStrategy targetToModel = null;
        if (model.getValueType() == String.class) {
            /*
             * Clearing a text box should set the model to null, not empty
             * string.
             */
            targetToModel = new UpdateValueStrategy()
                    .setConverter(new TypedConverter<String, String>(
                            String.class, String.class) {
                        @Override
                        protected String doConvert(String fromObject) {
                            if (fromObject != null && fromObject.isEmpty()) {
                                return null;
                            }
                            return fromObject;
                        }
                    });
        }
        return dbc.bindValue(target, model, targetToModel, null);
    }
    /**
     * Binds a text widget and makes it required.
     * 
     * @param text
     *            the widget
     * @param model
     *            the model observable
     * @param description
     *            the description for error messages
     * @return the binding
     */
    protected Binding bindRequiredText(Text text, IObservableValue model,
            String description) {
        Binding binding = bindText(text, model);
        setRequired(binding, description);
        return binding;
    }

    /**
     * Add required semantics to a binding.
     * 
     * @param binding
     *            the binding
     * @param description
     *            the description for error messages
     */
    protected void setRequired(Binding binding, String description) {
        RequiredFieldSupport.initFor(getDataBindingContext(), binding
                .getTarget(), description, false, SWT.BOTTOM | SWT.LEFT,
                binding);
    }

    /**
     * Add required semantics to a control.
     * 
     * @param target
     *            the control's observable
     * @param description
     *            the description for error messages
     * @param binding
     *            a binding that also contributes validation status, can be null
     */
    protected void setRequired(IObservable target, String description,
            Binding binding) {
        RequiredFieldSupport.initFor(getDataBindingContext(), target,
                description, false, SWT.BOTTOM | SWT.LEFT, binding);
    }

    /**
     * Configures a control to be enabled only when model contains a new order
     * (as opposed to a replace order).
     * 
     * @param control
     *            the control
     */
    protected void enableForNewOrderOnly(Control control) {
        getDataBindingContext().bindValue(
                SWTObservables.observeEnabled(control),
                getModel().getOrderObservable(),
                null,
                new UpdateValueStrategy().setConverter(new Converter(
                        NewOrReplaceOrder.class, Boolean.class) {
                    @Override
                    public Object convert(Object fromObject) {
                        return fromObject instanceof OrderSingle;
                    }
                }));
    }

    /**
     * Customizes a text widget to select the entire text when it receives focus
     * (makes it easy to change).
     * 
     * @param text
     *            the widget
     */
    protected void selectOnFocus(Text text) {
        text.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                ((Text) e.widget).selectAll();
            }
        });
    }

    /**
     * Hook up a listener to the targetControl that listens for {@link SWT#CR}
     * and invokes {@link #handleSend()}.
     * 
     * @param targetControl
     *            the control to hook up
     */
    protected void addSendOrderListener(Control targetControl) {
        targetControl.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.character == SWT.CR) {
                    if (getXSWTView().getSendButton().isEnabled()) {
                        handleSend();
                    }
                }
            }
        });
    }

    /**
     * This method "completes" the message by calling
     * {@link OrderTicketModel#completeMessage()}, sends the order via the
     * controller, then resets the message in the view model.
     */
    protected void handleSend() {
        try {
            // TODO: this logic should probably be in the controller
            PhotonPlugin plugin = PhotonPlugin.getDefault();
            mModel.completeMessage();
            NewOrReplaceOrder orderMessage = mModel.getOrderObservable().getTypedValue();
            plugin.getPhotonController().sendOrderChecked(orderMessage);
            mModel.clearOrderMessage();
        } catch (Exception e) {
            String errorMessage = e.getLocalizedMessage();
            PhotonPlugin.getMainConsoleLogger().error(errorMessage);
            showErrorMessage(errorMessage, IStatus.ERROR);
        }
    }

    /**
     * Show the given error message in this order ticket's error display area.
     * 
     * @param errorMessage
     *            the text of the error message
     * @param severity
     *            the severity of the error message, see {@link IStatus}
     */
    protected void showErrorMessage(String errorMessage, int severity) {
        Label errorMessageLabel = getXSWTView().getErrorMessageLabel();
        Label errorIconLabel = getXSWTView().getErrorIconLabel();

        if (errorMessage == null) {
            errorMessageLabel.setText(""); //$NON-NLS-1$
            errorIconLabel.setImage(null);
        } else {
            errorMessageLabel.setText(errorMessage);
            if (severity == IStatus.OK) {
                errorIconLabel.setImage(null);
            } else {
                if (severity == IStatus.ERROR)
                    errorIconLabel.setImage(FieldDecorationRegistry
                            .getDefault().getFieldDecoration(
                                    FieldDecorationRegistry.DEC_ERROR)
                            .getImage());
                else
                    errorIconLabel.setImage(FieldDecorationRegistry
                            .getDefault().getFieldDecoration(
                                    FieldDecorationRegistry.DEC_WARNING)
                            .getImage());
            }
        }
    }

    /**
     * Get the memento used for storing preferences and state for this view.
     * 
     * @return the memento
     */
    protected IMemento getMemento() {
        return mMemento;
    }

    /**
     * Stores the checked state of each of the custom fields in the view.
     */
    @Override
    public void saveState(IMemento memento) {
        TableItem[] items = getXSWTView().getCustomFieldsTable().getItems();
        for (int i = 0; i < items.length; i++) {
            TableItem item = items[i];
            String key = OrderTicketView.CUSTOM_FIELD_VIEW_SAVED_STATE_KEY_PREFIX
                    + item.getText(1);
            memento.putInteger(key, (item.getChecked() ? 1 : 0));
        }
    }

    /**
     * Set the focus on the Side control (in the case of a new order) or the
     * Quantity control (in the case of a replace order).
     */
    @Override
    public void setFocus() {
        IOrderTicket ticket = getXSWTView();
        if (ticket.getSideCombo().isEnabled()) {
            ticket.getSideCombo().setFocus();
        } else {
            ticket.getQuantityText().setFocus();
        }
    }

    @Override
    public void dispose() {
        getModel().getOrderObservable().removeValueChangeListener(
                mFocusListener);
        mObservablesManager.dispose();
        super.dispose();
    }
    /**
     * Provides labels from algos.
     *
     * @author <a href="mailto:colin@marketcetera.com">Colin DuPlantis</a>
     * @version $Id$
     * @since 2.4.0
     */
    @ClassVersion("$Id$")
    public final static class AlgoLabelProvider
            extends LabelProvider
    {
        /* (non-Javadoc)
         * @see org.eclipse.jface.viewers.LabelProvider#getText(java.lang.Object)
         */
        @Override
        public String getText(Object inElement)
        {
            if(inElement == null || !(inElement instanceof BrokerAlgo)) {
                return null;
            }
            return ((BrokerAlgo)inElement).getAlgoSpec().getName();
        }
    }
}
