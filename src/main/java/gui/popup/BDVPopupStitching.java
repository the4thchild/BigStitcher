package gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import javax.swing.JOptionPane;

import bdv.BigDataViewer;
import bdv.ViewerImgLoader;
import bdv.spimdata.WrapBasicImgLoader;
import bdv.tools.InitializeViewerState;
import bdv.tools.brightness.ConverterSetup;
import bdv.tools.brightness.MinMaxGroup;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import gui.AveragingProjectorARGB;
import gui.FilteredAndGroupedExporerPanel;
import gui.GroupedRowWindow;
import gui.MaximumProjectorARGB;
import gui.overlay.LinkOverlay;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.type.numeric.ARGBType;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.ViewSetupExplorerPanel;
import spim.fiji.spimdata.explorer.popup.BDVPopup;
import spim.fiji.spimdata.explorer.popup.BDVPopup.MyActionListener;
import spim.fiji.spimdata.explorer.util.ColorStream;
import spim.fiji.spimdata.imgloaders.AbstractImgLoader;

public class BDVPopupStitching extends BDVPopup
{
	/**
	 * 
	 */
	private static final long serialVersionUID = -8852442192041303045L;

	static LinkOverlay lo;
	
	public BDVPopupStitching(LinkOverlay lo1)
	{
		super();
		this.removeActionListener( this.getActionListeners()[0] );
		this.addActionListener( new MyActionListener() );
		
		lo = lo1;
	}
	
	
	
	public class MyActionListener implements ActionListener
	{
		@Override
		public void actionPerformed( final ActionEvent e )
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					// if BDV was closed by the user
					if ( bdv != null && !bdv.getViewerFrame().isVisible() )
						bdv = null;

					if ( bdv == null )
					{

						try
						{
							bdv = createBDV( panel );
						}
						catch (Exception e)
						{
							IOFunctions.println( "Could not run BigDataViewer: " + e );
							e.printStackTrace();
							bdv = null;
						}
					}
					else
					{
						closeBDV();
					}
				}
			}).start();
		}
	}
	
	public static void groupByChannel(BigDataViewer bdv, AbstractSpimData< ? > data)
	{
		// group ConverterSetups according to Channel
		HashMap< Channel, ArrayList< ConverterSetup > > groups = new HashMap<>();
		for (ConverterSetup cs : bdv.getSetupAssignments().getConverterSetups())
		{
			 Channel key = data.getSequenceDescription().getViewSetups().get( cs.getSetupId() ).getAttribute( Channel.class );
			 if (!groups.containsKey( key ))
				 groups.put( key, new ArrayList< ConverterSetup >() );
			 groups.get( key ).add( cs );
		}
		
		ArrayList<Channel> keyList = new ArrayList<>(groups.keySet());
		
		// nothing to group
		if (keyList.size() <= 1)
			return;
		
		for (int i = 1; i < keyList.size(); ++i)
		{
			ArrayList< ConverterSetup > cs = groups.get( keyList.get( i ) );
			
			// remove first setup from its group (group 0), creating a new one
			bdv.getSetupAssignments().removeSetupFromGroup( cs.get( 0 ), bdv.getSetupAssignments().getMinMaxGroups().get( 0 ));
			
			// move all other setups in group to the new MinMaxGroup (group i)
			for (int j = 1; j < cs.size(); ++j)
			{
				bdv.getSetupAssignments().moveSetupToGroup( cs.get( j ), bdv.getSetupAssignments().getMinMaxGroups().get( i ) );
			}
		}
		
	}
	
	public static void colorByChannel(BigDataViewer bdv, AbstractSpimData< ? > data)
	{
		// group ConverterSetups according to Channel
		HashMap< Channel, ArrayList< ConverterSetup > > groups = new HashMap<>();
		for (ConverterSetup cs : bdv.getSetupAssignments().getConverterSetups())
		{
			Channel key = data.getSequenceDescription().getViewSetups().get( cs.getSetupId() ).getAttribute( Channel.class );
			if (!groups.containsKey( key ))
				groups.put( key, new ArrayList< ConverterSetup >() );
			groups.get( key ).add( cs );
		}
		
		Iterator< ARGBType > colorIt = ColorStream.iterator();
		
		for (ArrayList< ConverterSetup > csg : groups.values())
		{
			ARGBType color = colorIt.next();
			for (ConverterSetup cs : csg)
				cs.setColor( color );
		}
	}
	

	public static BigDataViewer createBDV( final ExplorerWindow< ?, ? > panel )
	{
		if ( AbstractImgLoader.class.isInstance( panel.getSpimData().getSequenceDescription().getImgLoader() ) )
		{
			if ( JOptionPane.showConfirmDialog( null,
					"Opening <SpimData> dataset that is not suited for interactive browsing.\n" +
					"Consider resaving as HDF5 for better performance.\n" +
					"Proceed anyways?",
					"Warning",
					JOptionPane.YES_NO_OPTION ) == JOptionPane.NO_OPTION )
				return null;
		}

		// FIXME: do this somewhere else?
		WrapBasicImgLoader.wrapImgLoaderIfNecessary( panel.getSpimData() );
		
		ArrayList< ConverterSetup > convSetups = new ArrayList<>();
		ArrayList< SourceAndConverter< ? > > sources = new ArrayList<>();
		
		BigDataViewer.initSetups( panel.getSpimData(), convSetups, sources );
		
		BigDataViewer bdv = new BigDataViewer( 	convSetups,
												sources,
												panel.getSpimData(),
												panel.getSpimData().getSequenceDescription().getTimePoints().size(), 
												( ( ViewerImgLoader ) panel.getSpimData().getSequenceDescription().getImgLoader() ).getCache(),
												"BigDataViewer",
												null, 
												ViewerOptions.options().accumulateProjectorFactory( MaximumProjectorARGB.factory ) );


		bdv.getViewerFrame().setVisible( true );
		
		InitializeViewerState.initTransform( bdv.getViewer() );
		
			// if ( !bdv.tryLoadSettings( panel.xml() ) ) TODO: this should
			// work, but currently tryLoadSettings is protected. fix that.
		InitializeViewerState.initBrightness( 0.001, 0.999, bdv.getViewer(), bdv.getSetupAssignments() );

		FilteredAndGroupedExporerPanel.setFusedModeSimple( bdv, panel.getSpimData() );
//		if ( !bdv.tryLoadSettings( panel.xml() ) ) TODO: this should work, but currently tryLoadSettings is protected. fix that.
		//	InitializeViewerState.initBrightness( 0.001, 0.999, bdv.getViewer(), bdv.getSetupAssignments() );
		
			
		groupByChannel( bdv, panel.getSpimData() );
		colorByChannel( bdv, panel.getSpimData() );
			
		FilteredAndGroupedExporerPanel.updateBDV( bdv, panel.colorMode(), panel.getSpimData(), panel.firstSelectedVD(), ((GroupedRowWindow)panel).selectedRowsGroups());

		bdv.getViewer().addTransformListener( lo );
		bdv.getViewer().getDisplay().addOverlayRenderer( lo );

		return bdv;
		
	}

}
