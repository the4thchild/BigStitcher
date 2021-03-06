/*-
 * #%L
 * Multiview stitching of large datasets.
 * %%
 * Copyright (C) 2016 - 2017 Big Stitcher developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package net.preibisch.stitcher.gui.overlay;

import java.awt.Color;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Dimensions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.OverlayRenderer;
import net.imglib2.ui.TransformListener;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.preibisch.mvrecon.fiji.spimdata.explorer.SelectedViewDescriptionListener;
import net.preibisch.mvrecon.fiji.spimdata.stitchingresults.StitchingResults;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.overlap.SimpleBoundingBoxOverlap;

public class DemoLinkOverlay implements OverlayRenderer, TransformListener< AffineTransform3D >,SelectedViewDescriptionListener< AbstractSpimData<?> >
{

	private StitchingResults stitchingResults;
	private AbstractSpimData< ? > spimData;	
	private AffineTransform3D viewerTransform;	
	public boolean isActive;
	private ArrayList<Pair<Group<ViewId>, Group<ViewId>>> activeLinks;
	private double rThresh = .4;

	
	public DemoLinkOverlay( StitchingResults res, AbstractSpimData< ? > spimData)
	{
		this.stitchingResults = res;
		this.spimData = spimData;
		viewerTransform = new AffineTransform3D();
		isActive = false;
		activeLinks = new ArrayList<>();
	}
	
	
	@Override
	public void transformChanged(AffineTransform3D transform)
	{
		this.viewerTransform = transform;
		
	}

	@Override
	public void drawOverlays(Graphics g)
	{
		// dont do anything if the overlay was set to inactive or we have no Tile selected (no links to display)
		if (!isActive || activeLinks.size() == 0)
			return;
		
		for ( Pair<Group<ViewId>, Group<ViewId>> p: activeLinks)
		{
		
		// local coordinates of views, without BDV transform 
					final double[] lPos1 = new double[ 3 ];
					final double[] lPos2 = new double[ 3 ];
					// global coordianates, after BDV transform
					final double[] gPos1 = new double[ 3 ];
					final double[] gPos2 = new double[ 3 ];
					
					BasicViewDescription<?> vdA = spimData.getSequenceDescription().getViewDescriptions().get( p.getA().iterator().next() );
					BasicViewDescription<?> vdB = spimData.getSequenceDescription().getViewDescriptions().get( p.getB().iterator().next() );
					ViewRegistration vrA = spimData.getViewRegistrations().getViewRegistration(  p.getA().iterator().next() );
					ViewRegistration vrB = spimData.getViewRegistrations().getViewRegistration(  p.getB().iterator().next() );

					long[] sizeA = new long[vdA.getViewSetup().getSize().numDimensions()];
					long[] sizeB = new long[vdB.getViewSetup().getSize().numDimensions()];
					spimData.getSequenceDescription().getViewDescriptions().get( p.getA().iterator().next() ).getViewSetup().getSize().dimensions( sizeA );
					spimData.getSequenceDescription().getViewDescriptions().get( p.getB().iterator().next() ).getViewSetup().getSize().dimensions( sizeB );
					
					// TODO: this uses the transform of the first view in the set, maybe do something better?
					AffineTransform3D vt1 = spimData.getViewRegistrations().getViewRegistration( p.getA().iterator().next() ).getModel();
					AffineTransform3D vt2 = spimData.getViewRegistrations().getViewRegistration( p.getB().iterator().next() ).getModel();
					
					boolean overlaps = SimpleBoundingBoxOverlap.overlaps( SimpleBoundingBoxOverlap.getBoundingBox(	vdA.getViewSetup(), vrA ), SimpleBoundingBoxOverlap.getBoundingBox( vdB.getViewSetup(), vrB ) );
					
					if (!overlaps)
						continue;
					
					final AffineTransform3D transform = new AffineTransform3D();
					transform.preConcatenate( viewerTransform );

					for(int i = 0; i < 3; i++)
					{
						// start from middle of view
						lPos1[i] += sizeA[i] / 2;
						lPos2[i] += sizeB[i] / 2;
					}

					vt1.apply( lPos1, lPos1 );
					vt2.apply( lPos2, lPos2 );
					
					transform.apply( lPos1, gPos1 );
					transform.apply( lPos2, gPos2 );
					
					if (stitchingResults.getPairwiseResults().containsKey( p ) && stitchingResults.getPairwiseResultsForPair( p ).r() > rThresh)
					{
						g.setColor( Color.GREEN );
						
					}
					else
						continue;
						//g.setColor( Color.GRAY );
					
					g.drawLine((int) gPos1[0],(int) gPos1[1],(int) gPos2[0],(int) gPos2[1] );
		}
		
		
	}
	
	public void clearActiveLinks()
	{
		activeLinks.clear();
	}
	
	public void setActiveLinks(List<Pair<Group<ViewId>, Group<ViewId>>> vids)
	{
		activeLinks.clear();
		activeLinks.addAll( vids );
	}

	@Override
	public void setCanvasSize(int width, int height){}


	@Override
	public void selectedViewDescriptions(
			List< List< BasicViewDescription< ? extends BasicViewSetup > > > viewDescriptions)
	{
		List<Pair<Group<ViewId>, Group<ViewId>>> res = new ArrayList<>();
		for (int i = 0; i<viewDescriptions.size(); i++)
			for (int j = i+1; j<viewDescriptions.size(); j++)
			{
				Group<ViewId> groupA = new Group<>();
				groupA.getViews().addAll( viewDescriptions.get( i ) );
				Group<ViewId> groupB = new Group<>();
				groupB.getViews().addAll( viewDescriptions.get( j ) );
				res.add( new ValuePair< Group<ViewId>, Group<ViewId> >( groupA, groupB ) );
			}
		setActiveLinks( res );
		
	}


	@Override
	public void updateContent(AbstractSpimData< ? > data)
	{
	}


	@Override
	public void save()
	{
	}


	@Override
	public void quit()
	{
	}

}
