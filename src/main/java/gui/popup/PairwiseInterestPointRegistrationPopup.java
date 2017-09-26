package gui.popup;

import java.awt.Component;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import algorithm.SpimDataFilteringAndGrouping;
import algorithm.globalopt.TransformationTools;
import gui.StitchingExplorerPanel;
import gui.StitchingUIHelper;
import ij.gui.GenericDialog;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import spim.fiji.plugin.Calculate_Pairwise_Shifts;
import net.preibisch.mvrecon.fiji.plugin.Interest_Point_Detection;
import net.preibisch.mvrecon.fiji.plugin.Interest_Point_Registration;
import net.preibisch.mvrecon.fiji.plugin.interestpointregistration.TransformationModelGUI;
import net.preibisch.mvrecon.fiji.plugin.interestpointregistration.parameters.BasicRegistrationParameters;
import net.preibisch.mvrecon.fiji.plugin.interestpointregistration.parameters.GroupParameters.InterestpointGroupingType;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import net.preibisch.mvrecon.fiji.spimdata.explorer.GroupedRowWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPointLists;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.stitchingresults.PairwiseStitchingResult;
import net.preibisch.mvrecon.process.boundingbox.BoundingBoxMaximalGroupOverlap;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.PairwiseSetup;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class PairwiseInterestPointRegistrationPopup extends JMenu implements ExplorerWindowSetable
{

	private static final long serialVersionUID = -396274656320474433L;
	ExplorerWindow< ?, ? > panel;

	private JMenuItem withDetection;
	private JMenuItem withoutDetection;

	private boolean wizardMode;
	private boolean expertGrouping;

	public PairwiseInterestPointRegistrationPopup(String description, boolean wizardMode, boolean expertGrouping)
	{
		super( description );
		this.addActionListener( new MyActionListener(false) );

		this.wizardMode = wizardMode;
		this.expertGrouping = expertGrouping;

		withDetection = new JMenuItem( "With new Interest Points" );
		withoutDetection = new JMenuItem( "With existing Interest Points" );

		withDetection.addActionListener( new MyActionListener( false ) );
		withoutDetection.addActionListener( new MyActionListener( true ) );

		this.add( withDetection );
		this.add( withoutDetection );

		this.addMenuListener( new MenuListener()
		{
			@Override
			public void menuSelected(MenuEvent e)
			{
				if (SpimData2.class.isInstance( panel.getSpimData() ))
				{
					final List< ViewId > selectedViews = ((GroupedRowWindow)panel).selectedRowsViewIdGroups().stream().reduce( new ArrayList<>(), (x,y) -> {x.addAll( y ); return x;} );
					boolean allHaveIPs = true;
					final ViewInterestPoints viewInterestPoints = ((SpimData2)panel.getSpimData()).getViewInterestPoints();
					for (ViewId vid : selectedViews)
						if (panel.getSpimData().getSequenceDescription().getMissingViews() != null &&
							!panel.getSpimData().getSequenceDescription().getMissingViews().getMissingViews().contains( vid ))
						{
							if (!viewInterestPoints.getViewInterestPoints().containsKey( vid ) ||
								viewInterestPoints.getViewInterestPoints().get( vid ).getHashMap().size() < 1)
							{
								allHaveIPs = false;
								break;
							}
						}
					withoutDetection.setEnabled( allHaveIPs );
				}
			}

			@Override
			public void menuDeselected(MenuEvent e){}

			@Override
			public void menuCanceled(MenuEvent e){}

		} );
	}

	@Override
	public JComponent setExplorerWindow(
			final ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel )
	{
		this.panel = panel;
		return this;
	}

	public class MyActionListener implements ActionListener
	{
		private boolean existingInterestPoints;

		public MyActionListener(boolean existingInterestPoints)
		{
			this.existingInterestPoints = existingInterestPoints;
		}

		@Override
		public void actionPerformed(ActionEvent e)
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			if ( !SpimData2.class.isInstance( panel.getSpimData() ) )
			{
				IOFunctions.println( "Only supported for SpimData2 objects: " + this.getClass().getSimpleName() );
				return;
			}

			if (!GroupedRowWindow.class.isInstance( panel ))
			{
				IOFunctions.println( "Only supported for GroupedRowWindow panels: " + this.getClass().getSimpleName() );
				return;
			}

			new Thread( () ->
			{
				// get selected groups, filter missing views, get all present and selected vids
				final SpimData2 data = (SpimData2) panel.getSpimData();
				@SuppressWarnings("unchecked")
				FilteredAndGroupedExplorerPanel< SpimData2, ? > panelFG = (FilteredAndGroupedExplorerPanel< SpimData2, ? >) panel;
				SpimDataFilteringAndGrouping< SpimData2 > filteringAndGrouping = 	new SpimDataFilteringAndGrouping< SpimData2 >( (SpimData2) panel.getSpimData() );

				if (!expertGrouping)
				{
					// use whatever is selected in panel as filters
					filteringAndGrouping.addFilters( panelFG.selectedRowsGroups().stream().reduce( new ArrayList<>(), (x,y ) -> {x.addAll( y ); return x;}) );
				}
				else
				{
					filteringAndGrouping.askUserForFiltering( panelFG );
					if (filteringAndGrouping.getDialogWasCancelled())
						return;
				}

				if (!expertGrouping)
				{
					// get the grouping from panel and compare Tiles
					panelFG.getTableModel().getGroupingFactors().forEach( g -> filteringAndGrouping.addGroupingFactor( g ));
					filteringAndGrouping.addComparisonAxis( Tile.class );

					// compare by Channel if channels were ungrouped in UI
					if (!panelFG.getTableModel().getGroupingFactors().contains( Channel.class ))
						filteringAndGrouping.addComparisonAxis( Channel.class );

					// compare by Illumination if illums were ungrouped in UI
					if (!panelFG.getTableModel().getGroupingFactors().contains( Illumination.class ))
						filteringAndGrouping.addComparisonAxis( Illumination.class );
				}
				else
				{
					filteringAndGrouping.addComparisonAxis( Tile.class );
					filteringAndGrouping.askUserForGrouping( panelFG );
					if (filteringAndGrouping.getDialogWasCancelled())
						return;
				}

				boolean allViews2D = StitchingUIHelper.allViews2D( filteringAndGrouping.getFilteredViews() );
				if (allViews2D)
				{
					IOFunctions.println( "Interest point-based registration is currenty not supported for 2D: " + this.getClass().getSimpleName() );
					return;
				}


				if (Calculate_Pairwise_Shifts.processInterestPoint( data, filteringAndGrouping, existingInterestPoints ))
					if (wizardMode)
					{
						// ask user if they want to switch to preview mode
						if (panel instanceof StitchingExplorerPanel)
						{
							final int choice = JOptionPane.showConfirmDialog( (Component) panel, "Pairwise shift calculation done. Switch to preview mode?", "Preview Mode", JOptionPane.YES_NO_OPTION );
							if (choice == JOptionPane.YES_OPTION)
							{
								((StitchingExplorerPanel< ?, ? >) panel).setSavedFilteringAndGrouping( filteringAndGrouping );
								((StitchingExplorerPanel< ?, ? >) panel).togglePreviewMode(false);
							}
						}
					}

			}).start();

			panel.updateContent();
		}
	}

}
