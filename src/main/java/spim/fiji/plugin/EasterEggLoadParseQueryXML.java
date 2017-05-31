package spim.fiji.plugin;

import java.util.ArrayList;
import java.util.HashSet;

import input.FractalSpimDataGenerator;
import simulation.imgloader.SimulatedBeadsImgLoader2;
import spim.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;

public class EasterEggLoadParseQueryXML extends LoadParseQueryXML
{
	SpimData2 virtual;

	@Override
	protected boolean tryParsing( final String xmlfile, final boolean parseAllTypes )
	{
		final String input = xmlfile.toLowerCase().trim();

		if ( input.equals( "fractal" ) )
		{
			this.data = FractalSpimDataGenerator.createVirtualSpimData();
			this.attributes = getAttributes( data, comparator );
			final ArrayList< HashSet< Integer > > numEntitiesPerAttrib = entitiesPerAttribute();

			this.message1 = "This is a simulated fractal for testing.";
			this.message2 = GenericLoadParseQueryXML.getSpimDataDescription( data, attributes, numEntitiesPerAttrib, attributes.size() );
			this.color = GUIHelper.good;
			return true;
		}
		else if ( input.equals( "beads" ) )
		{
			this.data = SpimData2.convert( SimulatedBeadsImgLoader2.createSpimDataFromUserInput());
			this.attributes = getAttributes( data, comparator );
			final ArrayList< HashSet< Integer > > numEntitiesPerAttrib = entitiesPerAttribute();

			this.message1 = "These are simulated beads for testing.";
			this.message2 = GenericLoadParseQueryXML.getSpimDataDescription( data, attributes, numEntitiesPerAttrib, attributes.size() );
			this.color = GUIHelper.good;

			return true;
		}

		return super.tryParsing( xmlfile, parseAllTypes );
	}
}